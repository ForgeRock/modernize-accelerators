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
package org.forgerock.openig.modernize.provider;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

import org.forgerock.http.Client;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.modernize.common.User;
import org.forgerock.openig.modernize.impl.LegacyOpenSSOProvider;
import org.forgerock.util.promise.NeverThrowsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ForgeRockProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(ForgeRockProvider.class);
	private static final String QUERY = "?_queryFilter=userName+eq+%22{0}%22";

	/**
	 * 
	 * Method calls the client implementation to intercept the credentials from the
	 * request headers or body, and returns a {@link User} instance with filled
	 * userName and userPassword
	 * 
	 * @param legacyIAMProvider
	 * 
	 * @param request                  - ForgeRock HTTP {@link Request}
	 * @param getUserCredentialsMethod - Method name from the modernize library
	 * @return {@link User} - An user object with userName and userPassword set.
	 * @throws Exception
	 * 
	 */
	public static User getUserCredentials(LegacyOpenSSOProvider legacyIAMProvider, Request request,
			String getUserCredentialsMethod) {
		try {
			return legacyIAMProvider.getUserCredentials(request);
		} catch (Exception e) {
			LOGGER.error("getUserCredentials()::Exception while reading user's credentials: " + e);
		}
		return null;
	}

	/**
	 * 
	 * Calls IDM to get the information needed to determine if the user is migrated
	 * 
	 * @param getUserMigrationStatusEndpoint - The IDM end-point where we check if
	 *                                       the user is migrated
	 * @param userName                       - The userName of the current
	 *                                       requesting user
	 * @param openIdmUsernameHeader          - The HTTP header name where to send
	 *                                       the IDM administrator user
	 * @param openIdmUsername                - The IDM administrator userName
	 * @param openIdmPasswordHeader          - The HTTP header name where to send
	 *                                       the IDM administrator user's password
	 * @param openIdmPassword                - The IDM administrator password
	 * @param httpClientHandler              - The ForgeRock HTTP client handler
	 * @return - true if user is migrated, false otherwise
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 */
	public static boolean userMigrated(String getUserMigrationStatusEndpoint, String userName,
			String openIdmUsernameHeader, String openIdmUsername, String openIdmPasswordHeader, String openIdmPassword,
			HttpClientHandler httpClientHandler) throws IOException, NeverThrowsException, InterruptedException {
		String encodedQuery = MessageFormat.format(QUERY, "jane.doe");
		String getUserPathWithQuery = getUserMigrationStatusEndpoint + encodedQuery;
		LOGGER.debug("userMigrated()::Calling endpoint: " + getUserPathWithQuery);
		Request request = new Request();
		try {
			request.setMethod("GET").setUri(getUserPathWithQuery);
		} catch (URISyntaxException e) {
			LOGGER.error("userMigrated()::URISyntaxException: " + e);
		}
		request.getHeaders().add(openIdmUsernameHeader, openIdmUsername);
		request.getHeaders().add(openIdmPasswordHeader, openIdmPassword);
		request.getHeaders().add("Content-Type", "application/json");
		Client client = new Client(httpClientHandler);
		return userMigrated(client.send(request).getOrThrow());
	}

	/**
	 * 
	 * Reads the response received from IDM and determines if user is migrated
	 * 
	 * @param response - the response received from IDM
	 * @return - true if user is migrated, false otherwise
	 * @throws IOException
	 */
	private static boolean userMigrated(Response response) throws IOException {
		LOGGER.debug("userMigrated()::response.getBody(): " + response.getEntity().getJson());
		JsonValue entity = JsonValue.json(response.getEntity().getJson());
		if (entity != null && entity.get("resultCount") != null && entity.get("resultCount").asInteger() > 0) {
			return true;
		}
		return false;
	}

	/**
	 * 
	 * Authenticates the user in AM and retrieves the SSO token that will be set on
	 * the response
	 * 
	 * @param                             {@link User} - user object that holds
	 *                                    credentials for authentication
	 * @param openaAmAuthenticateURL      - the authentication URL for OpenAM
	 * @param acceptApiVersionHeader      - Accept API Version header name
	 * @param acceptApiVersionHeaderValue - Accept API version header value
	 * @param openAmCookieName            - The cookie name for OpenAM - default
	 *                                    iPlanetDirectoryPro
	 * @param httpClientHandler           - The ForgeRock HTTP client handler
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 */
	public static String authenticateUser(User user, String openaAmAuthenticateURL, String acceptApiVersionHeader,
			String acceptApiVersionHeaderValue, String openAmCookieName, HttpClientHandler httpClientHandler)
			throws NeverThrowsException, InterruptedException, IOException {
		Response entity = getCallbacks(openaAmAuthenticateURL, acceptApiVersionHeader, acceptApiVersionHeaderValue,
				httpClientHandler);
		if (entity != null) {
			LOGGER.debug("authenticateUser()::response.authenticateUser(): " + entity.getEntity().getJson());
			JsonValue callbacks = JsonValue.json(entity.getEntity().getJson());
			try {
				callbacks = createAuthenticationCallbacks(callbacks, user.getUserName(), user.getUserPassword());
				LOGGER.debug("authenticateUser()::callbacks: " + callbacks);
			} catch (IOException e) {
				LOGGER.error("authenticateUser()::Error: " + e);
			}
			return getCookie(openaAmAuthenticateURL, callbacks, acceptApiVersionHeader, acceptApiVersionHeaderValue,
					openAmCookieName, httpClientHandler);
		}
		return null;
	}

	/**
	 * 
	 * Gets the callbacks from first authenticate request in OpenAM
	 * 
	 * @param                             openaAmAuthenticateURL- the authentication
	 *                                    URL for OpenAM
	 * @param acceptApiVersionHeader      - Accept API Version header name
	 * @param acceptApiVersionHeaderValue - Accept API version header value
	 * @param httpClientHandler           - The ForgeRock HTTP client handler
	 * @return - response containing authentication callbacks
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 */
	private static Response getCallbacks(String openaAmAuthenticateURL, String acceptApiVersionHeader,
			String acceptApiVersionHeaderValue, HttpClientHandler httpClientHandler)
			throws NeverThrowsException, InterruptedException {
		Request request = new Request();
		try {
			request.setMethod("POST").setUri(openaAmAuthenticateURL);
		} catch (URISyntaxException e) {
			LOGGER.error("getCallbacks()::URISyntaxException: " + e);
		}
		request.getHeaders().add(acceptApiVersionHeader, acceptApiVersionHeaderValue);
		request.getHeaders().add("Content-Type", "application/json");
		Client client = new Client(httpClientHandler);
		return client.send(request).getOrThrow();
	}

	/**
	 * 
	 * Method that fills in the user credential in the authentication callbacks
	 * 
	 * @param callbacks - callbacks needed for authentication
	 * @param userId    - userName
	 * @param password  - userPassword
	 * @return - JSON body callbacks received from OpenAM with credentials set
	 * @throws IOException
	 */
	private static JsonValue createAuthenticationCallbacks(JsonValue callbacks, String userId, String password)
			throws IOException {
		callbacks.get("callbacks").get(0).get("input").get(0).put("value", userId);
		callbacks.get("callbacks").get(1).get("input").get(0).put("value", password);
		return callbacks;
	}

	/**
	 * 
	 * Sends the authentication request callbacks with credentials to OpenAM and
	 * extracts the SSO token from the authentication response headers
	 * 
	 * @param openaAmAuthenticateURL      - the authentication URL for OpenA
	 * @param callbacks                   - JSON body callbacks received from OpenAM
	 *                                    with credentials set
	 * @param acceptApiVersionHeader      - Accept API Version header name
	 * @param acceptApiVersionHeaderValue - Accept API version header value
	 * @param openAmCookieName            - The cookie name for OpenAM - default
	 *                                    iPlanetDirectoryPro
	 * @param httpClientHandler           - The ForgeRock HTTP client handler
	 * @return - The SSO token received following the authentication
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 * @throws IOException
	 * 
	 */
	private static String getCookie(String openaAmAuthenticateURL, JsonValue callbacks, String acceptApiVersionHeader,
			String acceptApiVersionHeaderValue, String openAmCookieName, HttpClientHandler httpClientHandler)
			throws NeverThrowsException, InterruptedException, IOException {
		Request request = new Request();
		try {
			request.setMethod("POST").setUri(openaAmAuthenticateURL);
		} catch (URISyntaxException e) {
			LOGGER.error("getCookie()::URISyntaxException: " + e);
		}
		request.setEntity(callbacks);
		// clearing headers - setting entity on the request adds automatically
		// content-length which can cause problems in some legacy systems
		request.getHeaders().clear();
		request.getHeaders().add(acceptApiVersionHeader, acceptApiVersionHeaderValue);
		request.getHeaders().add("Content-Type", "application/json");
		Client client = new Client(httpClientHandler);
		Response response = client.send(request).getOrThrow();

		if (response != null) {
			LOGGER.debug("getCookie()::response.getStatus(): " + response.getStatus());
			Headers responseHeaders = response.getHeaders();
			Map<String, List<String>> headersMap = responseHeaders.copyAsMultiMapOfStrings();
			List<String> cookies = headersMap.get("Set-Cookie");
			String cookie = cookies.stream().filter(x -> x.contains(openAmCookieName)).findFirst().orElse(null);
			LOGGER.debug("getCookie()::Cookie: " + cookie);
			return cookie;
		}

		return null;
	}

	/**
	 * 
	 * Calls IDM user URL in order to create a user
	 * 
	 * @param user                  {@link User} - An user object with userName and
	 *                              userPassword set, and extended user profile
	 *                              attributes
	 * @param openIdmUsernameHeader - The HTTP header name where to send the IDM
	 *                              administrator user
	 * @param openIdmUsername       - The IDM administrator userName
	 * @param openIdmPasswordHeader - The HTTP header name where to send the IDM
	 *                              administrator user's password
	 * @param openIdmPassword       - The IDM administrator password
	 * @param provisionUserEndpoint - The IDM create user URL
	 * @param httpClientHandler     - The ForgeRock HTTP client handler
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 * 
	 */
	public static void provisionUser(User user, String openIdmUsernameHeader, String openIdmUsername,
			String openIdmPasswordHeader, String openIdmPassword, String provisionUserEndpoint,
			HttpClientHandler httpClientHandler) throws NeverThrowsException, InterruptedException {
		LOGGER.debug("provisionUser()::Start");
		JsonValue jsonBody = createProvisioningRequestEntity(user);
		Request request = new Request();
		try {
			request.setMethod("POST").setUri(provisionUserEndpoint);
		} catch (URISyntaxException e) {
			LOGGER.error("provisionUser()::URISyntaxException: " + e);
		}
		request.setEntity(jsonBody);
		request.getHeaders().add(openIdmUsernameHeader, openIdmUsername);
		request.getHeaders().add(openIdmPasswordHeader, openIdmPassword);
		request.getHeaders().add("Content-Type", "application/json");
		Client client = new Client(httpClientHandler);
		Response response = client.send(request).getOrThrow();
		LOGGER.debug("provisionUser()::End: " + response.getStatus());
	}

	/**
	 * 
	 * Formats the create user request body as accepted by the create user end point
	 * 
	 * @param {@link User} - An user object with userName and userPassword set, and
	 *        extended user profile attributes
	 * @return - the user profile formatted as JSON
	 */
	private static JsonValue createProvisioningRequestEntity(User user) {
		LOGGER.debug("createProvisioningRequestEntity()::user: " + user);
		JsonValue value = JsonValue.json(JsonValue.object(JsonValue.field("givenName", user.getUserFirstName()),
				JsonValue.field("sn", user.getUserLastName()), JsonValue.field("mail", user.getUserEmail()),
				JsonValue.field("userName", user.getUserName()), JsonValue.field("password", user.getUserPassword())));
		LOGGER.debug("createProvisioningRequestEntity()::entity: " + value);
		return value;
	}

	/**
	 * 
	 * Creates an {@link User} object complete with extended user profile attributes
	 * 
	 * @param response             - The ForgeRock HTTP {@link Response}
	 * @param user                 - The user before getting the extended user
	 *                             profile atributes
	 * @param legacyIAMProvider
	 * @param getUserProfileMethod - Method name from the modernize library
	 * @return {@link User} - An user object with userName userPassword, and
	 *         attributes set
	 */
	public static User getExtendedUserProfile(Response response, User user, LegacyOpenSSOProvider legacyIAMProvider,
			String getUserProfileMethod) {
		return legacyIAMProvider.getExtendedUserAttributes(response, user.getUserName());
	}

	/**
	 * 
	 * Validate if authentication in Legacy IAM was successful
	 * 
	 * @param response                           - The ForgeRock HTTP
	 *                                           {@link Response}
	 * @param legacyIAMProvider
	 * @param validateLegacyAuthenticationMethod - Method name from the modernize
	 *                                           library
	 * @return
	 */
	public static boolean successfullLegacyAuthentication(Response response, LegacyOpenSSOProvider legacyIAMProvider,
			String validateLegacyAuthenticationMethod) {
		return legacyIAMProvider.validateLegacyAuthResponse(response);
	}

	/**
	 * 
	 * Creates the error message response in case something failed during the
	 * authentication/migration process
	 * 
	 * @return response body formatter
	 */
	public static JsonValue createFailedLoginError() {
		return JsonValue.json(JsonValue.object(JsonValue.field("code", 401), JsonValue.field("reason", "Unauthorized"),
				JsonValue.field("message", "Authentication Failed")));
	}
}
