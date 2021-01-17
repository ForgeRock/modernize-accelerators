/***************************************************************************
 *  Copyright 2021 ForgeRock AS
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

import java.io.IOException;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.modernize.impl.LegacyOpenSSOProvider;
import org.forgerock.openig.modernize.provider.ForgeRockProvider;
import org.forgerock.services.context.Context;
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
	private String openaAmAuthenticateURL;
	private String openAmCookieName;
	private String acceptApiVersionHeader;
	private String acceptApiVersionHeaderValue;
	private String setCookieHeader;

	private Map<String, Object> userAttributesMapping;

	private HttpClientHandler httpClientHandler;

	/**
	 * Main method that processes the IG filter chain
	 */
	@Override
	public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
		String requestMethod = request.getMethod();

		// Authentication calls should only use POST method
		if (!(POST.equalsIgnoreCase(requestMethod) && request.getUri().getPath().contains("authenticate"))) {
			return next.handle(context, request);
		}

		LOGGER.info("MigrationSsoFilter::filter > Started authentication request filtering");

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
			if (ForgeRockProvider.userMigrated(getUserMigrationStatusEndpoint, user.get(USERNAME).asString(),
					authorizationToken, httpClientHandler)) {
				// User is migrated already -> we're authenticating in AM and saving the cookie
				// that will be set on the response
				LOGGER.debug("MigrationSsoFilter::filter > User is migrated - processing migrated user request");
				return processMigratedAccess(user, next, context, request);
			} else {
				// User was not found in IDM, therefore saving the credentials and waiting for
				// response to determine if authentication was successful
				Promise<Response, NeverThrowsException> promise = next.handle(context, request);
				return promise.thenOnResult(response -> {
					try {
						processFirstLogin(response, user, authorizationToken);
					} catch (NeverThrowsException e) {
						LOGGER.error("MigrationSsoFilter::filter > NeverThrowsException: ", e);
					} catch (InterruptedException e) {
						LOGGER.error("MigrationSsoFilter::filter > InterruptedException: ", e);
						Thread.currentThread().interrupt();
					} catch (IOException e) {
						LOGGER.error("MigrationSsoFilter::filter > IOException: ", e);
					}
				});
			}
		} catch (Exception e) {
			LOGGER.error("MigrationSsoFilter::filter > Error in main filter method: ", e);
		}
		return next.handle(context, request);
	}

	private Promise<Response, NeverThrowsException> processMigratedAccess(JsonValue user, Handler next, Context context,
			Request request) throws NeverThrowsException, InterruptedException, IOException {

		String openAmCookie = ForgeRockProvider.authenticateUser(user, openaAmAuthenticateURL, acceptApiVersionHeader,
				acceptApiVersionHeaderValue, openAmCookieName, httpClientHandler);
		if (openAmCookie != null) {
			String requestCookie = openAmCookie;
			Promise<Response, NeverThrowsException> promise = next.handle(context, request);

			// Wait for response
			return promise.thenOnResult(response -> processSecondLogin(response, requestCookie));
		} else {
			LOGGER.warn("MigrationSsoFilter::filter > Authentication failed. Username or password invalid");
			Response response = new Response(Status.UNAUTHORIZED);
			response.setEntity(ForgeRockProvider.createFailedLoginError());
			return Promises.newResultPromise(response);
		}
	}

	/**
	 * 
	 * Processes first login on the route response. After the user logs in the
	 * legacy system, this method is triggered and we verify if the authentication
	 * is successful, and we get the extended user profile, followed by provisioning
	 * to IDM
	 * 
	 * @param response
	 * @param bindings
	 * @param user
	 * @param authorizationToken
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 * @throws IOException
	 * 
	 */
	private void processFirstLogin(Response response, JsonValue user, String authorizationToken)
			throws NeverThrowsException, InterruptedException, IOException {
		LOGGER.info("MigrationSsoFilter::processFirstLogin > Received authentication response");

		// Verify if authentication is successful
		if (ForgeRockProvider.successfulLegacyAuthentication(response, legacyIAMProvider)) {
			// Authentication successful, therefore retrieving extended user profile
			JsonValue extendedUserProfile = ForgeRockProvider.getExtendedUserProfile(response, user, legacyIAMProvider,
					userAttributesMapping);
			if (extendedUserProfile != null) {
				extendedUserProfile.add(PASSWORD, user.get(PASSWORD).asString());

				LOGGER.debug("MigrationSsoFilter::processFirstLogin > extendedUserProfile: {}", extendedUserProfile);
				LOGGER.debug("MigrationSsoFilter::processFirstLogin > Using authorization token: {}",
						authorizationToken);

				// Provision user in IDM
				ForgeRockProvider.provisionUser(extendedUserProfile, authorizationToken, provisionUserEndpoint,
						httpClientHandler);

				// Authenticate the user that was just provisioned
				String openAmCookie = ForgeRockProvider.authenticateUser(user, openaAmAuthenticateURL,
						acceptApiVersionHeader, acceptApiVersionHeaderValue, openAmCookieName, httpClientHandler);
				response.getHeaders().add(setCookieHeader, openAmCookie);
			}
		}
	}

	/**
	 * 
	 * Processes the second login on the route response. This method is triggered if
	 * the user was found migrated already, and the request reaches legacy IAM and
	 * successfully completes the authentication..
	 * 
	 * @param response
	 * @param bindings
	 * @param cookie
	 */
	private void processSecondLogin(Response response, String cookie) {
		LOGGER.debug("MigrationSsoFilter::processSecondLogin > Received authentication response - Setting cookie");
		response.getHeaders().add(setCookieHeader, cookie);
	}

	/**
	 * Create and initialize the filter, based on the configuration. The filter
	 * object is stored in the heap.
	 */
	public static class Heaplet extends GenericHeaplet {
		/**
		 * Create the filter object in the heap, setting the header name and value for
		 * the filter, based on the configuration.
		 *
		 * @return The filter object.
		 * @throws HeapException Failed to create the object.
		 */

		private static final Logger LOGGER = LoggerFactory.getLogger(Heaplet.class);

		@Override
		public Object create() throws HeapException {
			MigrationSsoFilter filter = new MigrationSsoFilter();
			filter.userAttributesMapping = config.get("userAttributesMapping").as(evaluatedWithHeapProperties())
					.asMap();
			filter.getUserMigrationStatusEndpoint = config.get("getUserMigrationStatusEndpoint")
					.as(evaluatedWithHeapProperties()).asString();
			filter.provisionUserEndpoint = config.get("provisionUserEndpoint").as(evaluatedWithHeapProperties())
					.asString();
			filter.openaAmAuthenticateURL = config.get("openaAmAuthenticateURL").as(evaluatedWithHeapProperties())
					.asString();
			filter.openAmCookieName = config.get("openAmCookieName").as(evaluatedWithHeapProperties()).asString();
			filter.acceptApiVersionHeader = config.get("acceptApiVersionHeader").as(evaluatedWithHeapProperties())
					.asString();
			filter.acceptApiVersionHeaderValue = config.get("acceptApiVersionHeaderValue")
					.as(evaluatedWithHeapProperties()).asString();
			filter.setCookieHeader = config.get("setCookieHeader").as(evaluatedWithHeapProperties()).asString();

			filter.legacyIAMProvider = new LegacyOpenSSOProvider();

			try {
				filter.httpClientHandler = new HttpClientHandler();
			} catch (HttpApplicationException e) {
				LOGGER.error("Error initializing HttpClientHandler: ", e);
			}

			return filter;
		}
	}
}
