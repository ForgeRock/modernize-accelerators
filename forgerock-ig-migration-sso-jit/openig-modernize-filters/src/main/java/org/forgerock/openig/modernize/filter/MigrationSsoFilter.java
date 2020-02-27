/***************************************************************************
 *  Copyright 2019 ForgeRock AS
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

import static org.forgerock.openig.el.Bindings.bindings;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.heap.Name;
import org.forgerock.openig.modernize.common.User;
import org.forgerock.openig.modernize.provider.ForgeRockProvider;
import org.forgerock.openig.secrets.FileSystemSecretStoreHeaplet;
import org.forgerock.openig.secrets.SecretsUtils;
import org.forgerock.secrets.GenericSecret;
import org.forgerock.secrets.NoSuchSecretException;
import org.forgerock.secrets.SecretStore;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrationSsoFilter implements Filter {

	private Logger LOGGER = LoggerFactory.getLogger(MigrationSsoFilter.class);

	private static final String GET_USER_CREDENTIALS_METHOD = "getUserCredentials";
	private static final String VALIDATE_LEGACY_AUTHENTICATION_METHOD = "validateLegacyAuthResponse";
	private static final String GET_USER_PROFILE_METHOD = "getExtendedUserAttributes";

	Class<?> legacyIAMProvider;

	private String getUserMigrationStatusEndpoint;
	private String provisionUserEndpoint;
	private String openIdmUsername;
	private String openaAmAuthenticateURL;
	private String openAmCookieName;

	private String openIdmUsernameHeader;
	private String openIdmPasswordHeader;
	private String acceptApiVersionHeader;
	private String acceptApiVersionHeaderValue;
	private String setCookieHeader;

	private String password;

	/**
	 * Main method that processes the IG filter chain
	 */
	@Override
	public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
		String requestMedhod = request.getMethod();
		if (requestMedhod != null && "post".equalsIgnoreCase(requestMedhod)) {
			// Call the client implementation via Reflection API to retrieve the user's
			// credentials
			User user = ForgeRockProvider.getUserCredentials(legacyIAMProvider, request, GET_USER_CREDENTIALS_METHOD);
			if (user != null) {
				LOGGER.info("User: " + user.toString());
				try {
					// Verify if user is already migrated in IDM
					if (ForgeRockProvider.userMigrated(getUserMigrationStatusEndpoint, user.getUserName(),
							openIdmUsernameHeader, openIdmUsername, openIdmPasswordHeader, password)) {
						LOGGER.error("User is migrated.");

						// User is migrated already -> we're logging it in AM and saving the cookie to
						// set it on the response
						String openAmCookie = ForgeRockProvider.authenticateUser(user, openaAmAuthenticateURL,
								acceptApiVersionHeader, acceptApiVersionHeaderValue, openAmCookieName);
						if (openAmCookie != null) {
							String requestCookie = openAmCookie;
							Promise<Response, NeverThrowsException> promise = next.handle(context, request);

							// Wait for response
							return promise.thenOnResult(response -> processSecondLogin(response,
									bindings(context, request, response), requestCookie));
						} else {
							LOGGER.error("Authentication failed. Username or password invalid.");
							Response response = new Response(Status.UNAUTHORIZED);
							response.setEntity(ForgeRockProvider.createFailedLoginError());
							return Promises.newResultPromise(response);
						}
					} else {

						// User was not found in IDM, therefore saving the credentials and waiting for
						// response to determine if authentication was successfull
						Promise<Response, NeverThrowsException> promise = next.handle(context, request);
						return promise.thenOnResult(
								response -> processFirstLogin(response, bindings(context, request, response), user));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return next.handle(context, request);
	}

	/**
	 * 
	 * Processes first login on the route response. After the user logs in the
	 * legacy system, this method is triggered and we verify if the authentication
	 * is successfull, and we get the extended user profile, followed by
	 * provisioning to IDM
	 * 
	 * @param response
	 * @param bindings
	 * @param user
	 * 
	 */
	private void processFirstLogin(Response response, Bindings bindings, User user) {
		LOGGER.error("processFirstLoginWithAttributes()::Received authentication response.");
		LOGGER.error("processFirstLoginWithAttributes()::user: " + user.toString());

		// Verify if authentication is successfull with the client implementation via
		// Reflection API
		if (ForgeRockProvider.successfullLegacyAuthentication(response, legacyIAMProvider,
				VALIDATE_LEGACY_AUTHENTICATION_METHOD)) {

			// Authentication successfull, therefore retrieving extended user profile
			User extendedUserProfile = ForgeRockProvider.getExtendedUserProfile(response, user, legacyIAMProvider,
					GET_USER_PROFILE_METHOD);
			if (extendedUserProfile != null) {
				extendedUserProfile.setUserName(user.getUserName());
				extendedUserProfile.setUserPassword(user.getUserPassword());

				// Provision use in IDM
				ForgeRockProvider.provisionUser(extendedUserProfile, openIdmUsernameHeader, openIdmUsername,
						openIdmPasswordHeader, password, provisionUserEndpoint);
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
	private void processSecondLogin(Response response, Bindings bindings, String cookie) {
		LOGGER.error("processSecondLogin()::Received authentication response - Setting cookie.");
		response.getHeaders().add(setCookieHeader, cookie);
	}

	/**
	 * Create and initialize the filter, based on the configuration. The filter
	 * object is stored in the heap.
	 */
	public static class Heaplet extends GenericHeaplet {

		private Logger LOGGER = LoggerFactory.getLogger(Heaplet.class);

		/**
		 * Create the filter object in the heap, setting the header name and value for
		 * the filter, based on the configuration.
		 *
		 * @return The filter object.
		 * @throws HeapException Failed to create the object.
		 */
		@SuppressWarnings("unchecked")
		@Override
		public Object create() throws HeapException {
			MigrationSsoFilter filter = new MigrationSsoFilter();
			filter.getUserMigrationStatusEndpoint = config.get("getUserMigrationStatusEndpoint")
					.as(evaluatedWithHeapProperties()).asString();
			filter.provisionUserEndpoint = config.get("provisionUserEndpoint").as(evaluatedWithHeapProperties())
					.asString();
			filter.openIdmUsername = config.get("openIdmUsername").as(evaluatedWithHeapProperties()).asString();
			filter.openaAmAuthenticateURL = config.get("openaAmAuthenticateURL").as(evaluatedWithHeapProperties())
					.asString();
			filter.openAmCookieName = config.get("openAmCookieName").as(evaluatedWithHeapProperties()).asString();
			filter.openIdmUsernameHeader = config.get("openIdmUsernameHeader").as(evaluatedWithHeapProperties())
					.asString();
			filter.openIdmPasswordHeader = config.get("openIdmPasswordHeader").as(evaluatedWithHeapProperties())
					.asString();
			filter.acceptApiVersionHeader = config.get("acceptApiVersionHeader").as(evaluatedWithHeapProperties())
					.asString();
			filter.acceptApiVersionHeaderValue = config.get("acceptApiVersionHeaderValue")
					.as(evaluatedWithHeapProperties()).asString();
			filter.setCookieHeader = config.get("setCookieHeader").as(evaluatedWithHeapProperties()).asString();

			// Load framework impl
			try {
				filter.legacyIAMProvider = Class
						.forName(config.get("migrationImplClassName").as(evaluatedWithHeapProperties()).asString());
			} catch (JsonValueException e) {
				LOGGER.error("No class configured in property migrationImplClassName or property missing: "
						+ e.getMessage());
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				LOGGER.error("Class configured in migrationImplClassName was not found: " + e.getMessage());
				e.printStackTrace();
			}

			// Secrets
			final FileSystemSecretStoreHeaplet heaplet = new FileSystemSecretStoreHeaplet();
			final JsonValue evaluated = config.as(evaluatedWithHeapProperties());
			String passwordSecretId = evaluated.get("openIdmPasswordSecretId").asString();
			try {
				final SecretStore<GenericSecret> store = (SecretStore<GenericSecret>) heaplet
						.create(Name.of("MigrationSsoFilter"), config, heap);
				if (store != null) {
					String password = SecretsUtils.getPasswordSecretIdOrPassword(heaplet.getSecretService(),
							JsonValue.json(passwordSecretId), JsonValue.json(passwordSecretId), LOGGER);
					filter.password = password;
				}
			} catch (NoSuchSecretException e) {
				LOGGER.error("Error reading secret with id " + passwordSecretId + ": " + e.getMessage());
				e.printStackTrace();
			}

			return filter;
		}
	}
}
