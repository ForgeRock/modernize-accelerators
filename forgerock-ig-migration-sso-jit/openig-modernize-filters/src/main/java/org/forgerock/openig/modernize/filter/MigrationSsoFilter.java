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

import static org.forgerock.openig.modernize.provider.ForgeRockProvider.getErrorResponse;
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
import org.forgerock.openig.modernize.LegacyIAMProvider;
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
	private LegacyIAMProvider legacyIAMProvider;

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
		if (POST.equalsIgnoreCase(requestMethod) && !request.getEntity().isRawContentEmpty()) {
			LOGGER.info("MigrationSsoFilter::filter > Started authentication request filtering");
			JsonValue user = ForgeRockProvider.getUserCredentials(legacyIAMProvider, request);

			// Continue normal authentication in legacy IAM if no user found
			if (user == null) {
				return next.handle(context, request);
			}
			String authorizationToken = request.getHeaders().getFirst(AUTHORIZATION);

			// Verify if user is already migrated in IDM
			Promise<Boolean, NeverThrowsException> isUserMigrated = ForgeRockProvider.userMigrated(
					getUserMigrationStatusEndpoint, user.get(USERNAME).asString(), authorizationToken,
					httpClientHandler);

			return isUserMigrated.thenAsync(resultMigrated -> {
				if (Boolean.TRUE.equals(resultMigrated)) {
					LOGGER.info("MigrationSsoFilter::filter > User is migrated - processing migrated user request");

					return processMigratedAccess(user, next, context, request);
				} else {
					LOGGER.info("MigrationSsoFilter::filter > User is not migrated - allowing request to pass");

					Promise<Response, NeverThrowsException> promise = next.handle(context, request);
					return promise.thenAsync(response -> processFirstLogin(response, user, authorizationToken));
				}
			});
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
	private Promise<Response, NeverThrowsException> processMigratedAccess(JsonValue user, Handler next, Context context,
			Request request) {

		Promise<Response, NeverThrowsException> openAmCookie = ForgeRockProvider.authenticateUser(user,
				openAmAuthenticateURL, acceptApiVersionHeader, acceptApiVersionHeaderValue, httpClientHandler);
		return openAmCookie.thenAsync(setCookies(next, context, request));
	}

	/**
	 *
	 * Executed when user is already migrated, this async function authenticates the user in AM by passing
	 * the request to the end of the filter chain, then fetching and setting the obtained cookie on top of the
	 * legacy cookie the response.
	 *
	 * @param next	  - handler to the next filter in chain
	 * @param context - current's filter context
	 * @param request - current's filter request
	 * @return - the final response containing both cookies
	 */
	private AsyncFunction<Response, Response, NeverThrowsException> setCookies(Handler next, Context context, Request request) {
		return cookieResponse -> {
			String openAmCookie = ForgeRockProvider.extractCookie(cookieResponse, openAmCookieName);

			LOGGER.info("MigrationSsoFilter::setCookies > Extracted OpenAmCookie: {}", openAmCookie);
			if (openAmCookie != null) {
				// Let the request pass further, obtaining the legacy cookie
				Promise<Response, NeverThrowsException> promise = next.handle(context, request);

				// Then return response with the added extracted cookie alongside the legacy cookie
				return promise.thenAsync(legacyResponse -> {
					legacyResponse.getHeaders().add(setCookieHeader, openAmCookie);
					return Promises.newResultPromise(legacyResponse);
				});
			}

			LOGGER.error("MigrationSsoFilter::setCookies > Authentication failed. Username or password invalid");
			return getErrorResponse(Status.UNAUTHORIZED);
		};
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
	private Promise<Response, NeverThrowsException> processFirstLogin(Response response, JsonValue user, String authorizationToken) {
		LOGGER.info("MigrationSsoFilter::processFirstLogin > Received authentication response: {}",
				response.getHeaders().asMapOfHeaders());

		// Authentication successful, therefore retrieving extended user profile
		Promise<Response, NeverThrowsException> extendedUserProfile = ForgeRockProvider.getExtendedUserProfile(response,
				user, legacyIAMProvider, httpClientHandler);
		return extendedUserProfile.thenAsync(provisionUser(response, user, authorizationToken));
	}

	/**
	 *
	 * This async method is executed when the user is not migrated, right after the filter has obtained
	 * the extended user profile. Creates the user entry in IDM, authenticates the said user and sets
	 * the obtained authentication cookie on the request.
	 *
	 * @param resultResponse	 - the final response that will be returned by this filter, containing the legacy cookie
	 * @param user				 - JsonValue describing the user to provision
	 * @param authorizationToken - token needed to for the IDM provisioning request
	 * @return - the final response handled by this filter, containing both cookies
	 */
	private AsyncFunction<Response, Response, NeverThrowsException> provisionUser(Response resultResponse, JsonValue user,
			String authorizationToken) {
		LOGGER.info("MigrationSsoFilter::provisionUser > Start");

		return response -> {
			if (!response.getStatus().isSuccessful()) {
				return Promises.newResultPromise(new Response(Status.BAD_REQUEST));
			}

			JsonValue extendedUserProfile = setUserProperties(response, userAttributesMapping);
			LOGGER.info("MigrationSsoFilter::provisionUser > extendedUserProfile: {}", extendedUserProfile);

			if (extendedUserProfile != null) {
				extendedUserProfile.remove(PASSWORD);
				extendedUserProfile.add(PASSWORD, user.get(PASSWORD).asString());

				// Provision user in IDM
				Promise<Response, NeverThrowsException> provisionResponse = ForgeRockProvider.provisionUser(
						extendedUserProfile, authorizationToken, provisionUserEndpoint, httpClientHandler);

				Promise<Response, NeverThrowsException> authenticationResponse =
						provisionResponse.thenAsync(authenticateProvisionedUser(user));
				return authenticationResponse.thenAsync(setAuthenticationCookie(resultResponse));
			}

			return Promises.newResultPromise(new Response(Status.BAD_REQUEST));
		};
	}

	/**
	 *
	 * Async method executed when the user is not provisioned, called after the provisioning of the user.
	 * Authenticates the recently provisioned user.
	 *
	 * @param user - user to authenticate
	 * @return - response containing the authentication cookie
	 */
	private AsyncFunction<Response, Response, NeverThrowsException> authenticateProvisionedUser(JsonValue user) {
		return provisionedResponse -> {
			LOGGER.info("MigrationSsoFilter::authenticateProvisionedUser > User provisioning response status: {}",
					provisionedResponse.getStatus());

			if (provisionedResponse.getStatus().equals(Status.CREATED)) {
				// Authenticate the user that was just provisioned
				return ForgeRockProvider.authenticateUser(user, openAmAuthenticateURL, acceptApiVersionHeader,
						acceptApiVersionHeaderValue, httpClientHandler);
			}

			return getErrorResponse(Status.UNAUTHORIZED);
		};
	}

	/**
	 *
	 * Async method that adds the cookie of the previous response (caller promise) to the given response.
	 *
	 * @param cookieDestination - response where to add the cookie
	 * @return - cookieDestination containing the cookie of the caller's resulted response
	 */
	private AsyncFunction<Response, Response, NeverThrowsException> setAuthenticationCookie(Response cookieDestination) {
		return response -> {
			cookieDestination.getHeaders().add(setCookieHeader,
					response.getHeaders().asMapOfHeaders().get(setCookieHeader).getFirstValue());
			return Promises.newResultPromise(cookieDestination);
		};
	}

	/**
	 *
	 * Creates the User object that will be provisioned into the IDM platform.
	 *
	 * @param responseEntity 		- response containing the user attributes on the entity
	 * @param userAttributesMapping - mapping of the attributes over the IDM schema
	 * @return - JsonValue describing the user's attributes
	 */
	private JsonValue setUserProperties(Response responseEntity, Map<String, Object> userAttributesMapping) {

		try {
			LOGGER.info("LegacyOpenSSOProvider::setUserProperties > responseEntity: {}", responseEntity.getEntity());
			JsonValue entity = JsonValue.json(responseEntity.getEntity().getJson());
			JsonValue userAttributes = JsonValue.json(JsonValue.object());

			Iterator<Map.Entry<String, Object>> itr = userAttributesMapping.entrySet().iterator();
			LOGGER.info("LegacyOpenSSOProvider::setUserProperties > userAttributesMapping: {}", userAttributesMapping);

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
			LOGGER.error("LegacyOpenSSOProvider::setUserProperties > Null or invalid response entity: {0}", e);
		}
		return null;
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
