/***************************************************************************
 *  Copyright 2019-2021 ForgeRock AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ***************************************************************************/
package org.forgerock.openig.modernize.filter;

import static org.forgerock.openig.modernize.utils.FilterConstants.Attributes.PASSWORD;
import static org.forgerock.openig.modernize.utils.FilterConstants.Attributes.USERNAME;
import static org.forgerock.openig.modernize.utils.FilterConstants.Headers.AUTHORIZATION;
import static org.forgerock.openig.modernize.utils.FilterConstants.Methods.POST;
import static org.forgerock.openig.util.JsonValues.requiredHeapObject;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Keys;
import org.forgerock.openig.modernize.impl.LegacyOpenSSOProvider;
import org.forgerock.openig.modernize.provider.ForgeRockProvider;
import org.forgerock.services.context.Context;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrationSsoFilter implements Filter {

	private static final Logger LOGGER = LoggerFactory.getLogger(MigrationSsoFilter.class);

	private LegacyOpenSSOProvider legacyIAMProvider;

	private String getUserMigrationStatusEndpoint;
	private String provisionUserEndpoint;
	private String openAmAuthenticateURL;
	private String openAmCookieName;
	private String acceptApiVersionHeader;
	private String acceptApiVersionHeaderValue;
	private String setCookieHeader;

	private Map<String, Object> userAttributesMapping;
	private Handler httpClientHandler;

	/**
	 * Main method that processes the IG filter chain
	 */
	@Override
	public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
		String requestMethod = request.getMethod();

		// Authentication calls should only use POST method
		// && request.getUri().getPath().contains("authenticate")
		try {
			if (POST.equalsIgnoreCase(requestMethod) && request.getEntity().getJson() != null) {
				LOGGER.debug("MigrationSsoFilter::filter > Started authentication request filtering");
				JsonValue user = ForgeRockProvider.getUserCredentials(legacyIAMProvider, request);

				// continue normal authentication in legacy IAM if no user found
				if (user == null) {
					return next.handle(context, request);
				}

				LOGGER.debug("MigrationSsoFilter::filter > User: {}", user);
				try {
					String authorizationToken = request.getHeaders().getFirst(AUTHORIZATION);
					LOGGER.debug("MigrationSsoFilter::filter > obtained token: {}", authorizationToken);

					// Verify if user is already migrated in IDM
					Promise<Boolean, NeverThrowsException> isUserMigrated = ForgeRockProvider.userMigrated(
							getUserMigrationStatusEndpoint, user.get(USERNAME).asString(), authorizationToken,
							httpClientHandler);

					Promises.when(isUserMigrated).then(results -> {
						LOGGER.debug("MigrationSsoFilter::filter > results.get(0): {}", results.get(0));
						if (Boolean.TRUE.equals(results.get(0))) {
							LOGGER.debug(
									"MigrationSsoFilter::filter > User is migrated - processing migrated user request");
							return processMigratedAccess(user, next, context, request);
						} else {
							LOGGER.debug(
									"MigrationSsoFilter::filter > User is not migrated - allowing request to pass");
							Promise<Response, NeverThrowsException> promise = next.handle(context, request);
							return promise
									.thenOnResult(response -> processFirstLogin(response, user, authorizationToken));
						}
					});

				} catch (Exception e) {
					LOGGER.error("MigrationSsoFilter::filter > Error in main filter method: ", e);
				}
			}
		} catch (IOException e) {
			LOGGER.error("MigrationSsoFilter::filter > IOException: ", e);
		}

		return next.handle(context, request);
	}

	/**
	 * Authenticates and manages an already migrated user's access
	 *
	 * @param user    - user to authenticate
	 * @param next    - filter's handler
	 * @param context - current filter's context
	 * @param request - current filter's request managed so far
	 * @return - the authentication result response
	 */
	private Promise<Object, NeverThrowsException> processMigratedAccess(JsonValue user, Handler next, Context context,
			Request request) {

		Promise<Response, NeverThrowsException> openAmCookie = ForgeRockProvider.authenticateUser(user,
				openAmAuthenticateURL, acceptApiVersionHeader, acceptApiVersionHeaderValue, httpClientHandler);

		return Promises.when(openAmCookie).then(responses -> manageCookies(next, context, request, responses.get(0)));
	}

	private Promise<Response, NeverThrowsException> manageCookies(Handler next, Context context, Request request,
			Response cookieResponse) {
		String openAmCookie = ForgeRockProvider.extractCookie(cookieResponse, openAmCookieName);

		LOGGER.debug("MigrationSsoFilter::manageCookies > openAmCookie: {}", openAmCookie);
		if (openAmCookie != null) {
			Promise<Response, NeverThrowsException> promise = next.handle(context, request);
			return Promises.when(promise).then(response -> processSecondLogin(response.get(0), openAmCookie));
		}

		LOGGER.debug("MigrationSsoFilter::fetchCookie > Authentication failed. Username or password invalid");
		return Promises.newResultPromise(getUnauthorizedResponse());
	}

	/**
	 * 
	 * Processes first login on the route response. After the user logs in the
	 * legacy system, this method is triggered and we verify if the authentication
	 * is successful, and we get the extended user profile, followed by provisioning
	 * to IDM
	 * 
	 * @param response           - response of the legacy system
	 * @param user               - user to authenticate
	 * @param authorizationToken - token used to authenticate the user
	 */
	private void processFirstLogin(Response response, JsonValue user, String authorizationToken) {
		LOGGER.debug("MigrationSsoFilter::processFirstLogin > Received authentication response: {}",
				response.getHeaders().asMapOfHeaders());

		// Authentication successful, therefore retrieving extended user profile
		Promise<Response, NeverThrowsException> extendedUserProfile = ForgeRockProvider.getExtendedUserProfile(response,
				user, legacyIAMProvider, userAttributesMapping, httpClientHandler);
		extendedUserProfile.thenAsync(provisionUser(user, authorizationToken));
	}

	private AsyncFunction<Response, Void, NeverThrowsException> provisionUser(JsonValue user,
			String authorizationToken) {
		LOGGER.debug("MigrationSsoFilter::provisionUser > Start");
		return response -> {
			JsonValue extendedUserProfile = setUserProperties(response, userAttributesMapping);
			if (extendedUserProfile != null) {
				extendedUserProfile.add(PASSWORD, user.get(PASSWORD).asString());

				LOGGER.debug("MigrationSsoFilter::processFirstLogin > extendedUserProfile: {}", extendedUserProfile);
				LOGGER.debug("MigrationSsoFilter::processFirstLogin > Using authorization token: {}",
						authorizationToken);

				// Provision user in IDM
				Promise<Response, NeverThrowsException> provisionResponse = ForgeRockProvider.provisionUser(
						extendedUserProfile, authorizationToken, provisionUserEndpoint, httpClientHandler);

				provisionResponse.thenOnResult(createdResponse -> {
					LOGGER.info("MigrationSsoFilter::provisionUser > createdResponse.getStatus(): {}",
							createdResponse.getStatus());

					if (createdResponse.getStatus().equals(Status.CREATED)) {
						// Authenticate the user that was just provisioned
						Promise<Response, NeverThrowsException> openAmCookie = ForgeRockProvider.authenticateUser(user,
								openAmAuthenticateURL, acceptApiVersionHeader, acceptApiVersionHeaderValue,
								httpClientHandler);

						response.getHeaders().add(setCookieHeader, openAmCookie);
					}
				});
			}
			return null;
		};
	}

	/**
	 *
	 * Creates the User object that will be provisioned into the IDM platform.
	 *
	 * @param responseEntity        - response containing the user attributes on the
	 *                              entity
	 * @param userAttributesMapping - mapping of the attributes over the IDM schema
	 * @return - a JsonValue describing the user's attributes
	 */
	private JsonValue setUserProperties(Response responseEntity, Map<String, Object> userAttributesMapping) {

		try {
			LOGGER.debug("LegacyOpenSSOProvider::setUserProperties > responseEntity: {}", responseEntity.getEntity());
			JsonValue entity = JsonValue.json(responseEntity.getEntity().getJson());
			JsonValue userAttributes = JsonValue.json(JsonValue.object());

			Iterator<Map.Entry<String, Object>> itr = userAttributesMapping.entrySet().iterator();
			LOGGER.debug("LegacyOpenSSOProvider::setUserProperties > userAttributesMapping: {}", userAttributesMapping);

			while (itr.hasNext()) {
				Map.Entry<String, Object> entry = itr.next();
				String key = entry.getKey();
				String value = entry.getValue().toString();
				if (entity.get(key).isList()) {
					userAttributes.put(value, entity.get(key).get(0).asString());
				} else {
					userAttributes.put(value, entity.get(key).asString());
				}
			}
			return userAttributes;
		} catch (IOException e) {
			LOGGER.error("LegacyOpenSSOProvider::setUserProperties > Null or invalid response entity: ", e);
		}
		return null;
	}

	/**
	 * 
	 * Processes the second login on the route response. This method is triggered if
	 * the user was found migrated already, and the request reaches legacy IAM and
	 * successfully completes the authentication.
	 * 
	 * @param response - the response
	 * @param cookie   - the cookie set at the authentication phase
	 */
	private Response processSecondLogin(Response response, String cookie) {
		LOGGER.debug("MigrationSsoFilter::processSecondLogin > Received authentication response - Setting cookie");
		response.getHeaders().add(setCookieHeader, cookie);
		return response;
	}

	/**
	 *
	 * Creates and returns a a 401: Unauthorized response
	 *
	 * @return - an unauthorized response
	 */
	private Response getUnauthorizedResponse() {
		Response unauthorizedResponse = new Response(Status.UNAUTHORIZED);
		unauthorizedResponse.setEntity(ForgeRockProvider.createFailedLoginError());
		return unauthorizedResponse;
	}

	/**
	 * Create and initialize the filter, based on the configuration. The filter
	 * object is stored in the heap.
	 */
	public static class Heaplet extends GenericHeaplet {
		/**
		 * Create the filter object in the heap, setting the header name and value for
		 * the filter, based on the configuration.
		 */

		@Override
		public Object create() throws HeapException {
			MigrationSsoFilter filter = new MigrationSsoFilter();
			filter.userAttributesMapping = config.get("userAttributesMapping").as(evaluatedWithHeapProperties())
					.asMap();
			filter.getUserMigrationStatusEndpoint = config.get("getUserMigrationStatusEndpoint")
					.as(evaluatedWithHeapProperties()).asString();
			filter.provisionUserEndpoint = config.get("provisionUserEndpoint").as(evaluatedWithHeapProperties())
					.asString();
			filter.openAmAuthenticateURL = config.get("openaAmAuthenticateURL").as(evaluatedWithHeapProperties())
					.asString();
			filter.openAmCookieName = config.get("openAmCookieName").as(evaluatedWithHeapProperties()).asString();
			filter.acceptApiVersionHeader = config.get("acceptApiVersionHeader").as(evaluatedWithHeapProperties())
					.asString();
			filter.acceptApiVersionHeaderValue = config.get("acceptApiVersionHeaderValue")
					.as(evaluatedWithHeapProperties()).asString();
			filter.setCookieHeader = config.get("setCookieHeader").as(evaluatedWithHeapProperties()).asString();

			filter.legacyIAMProvider = new LegacyOpenSSOProvider();

			filter.httpClientHandler = config.get("migrationClientHandler")
					.defaultTo(Keys.FORGEROCK_CLIENT_HANDLER_HEAP_KEY).as(requiredHeapObject(heap, Handler.class));

			return filter;
		}
	}
}
