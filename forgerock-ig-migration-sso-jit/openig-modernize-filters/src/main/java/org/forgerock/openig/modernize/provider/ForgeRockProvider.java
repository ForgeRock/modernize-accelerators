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
package org.forgerock.openig.modernize.provider;

import static org.forgerock.openig.modernize.utils.FilterConstants.Attributes.PASSWORD;
import static org.forgerock.openig.modernize.utils.FilterConstants.Attributes.USERNAME;
import static org.forgerock.openig.modernize.utils.FilterConstants.Headers.APPLICATION_JSON;
import static org.forgerock.openig.modernize.utils.FilterConstants.Headers.AUTHORIZATION;
import static org.forgerock.openig.modernize.utils.FilterConstants.Headers.CONTENT_TYPE;
import static org.forgerock.openig.modernize.utils.FilterConstants.Methods.GET;
import static org.forgerock.openig.modernize.utils.FilterConstants.Methods.POST;

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
import org.forgerock.openig.modernize.impl.LegacyOpenSSOProvider;
import org.forgerock.util.promise.NeverThrowsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ForgeRockProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(ForgeRockProvider.class);
	private static final String QUERY = "?_queryFilter=userName+eq+%22{0}%22";

	private ForgeRockProvider() {
		throw new IllegalStateException("Instantiation is not allowed");
	}

	/**
	 * 
	 * Method calls the client implementation to intercept the credentials from the
	 * request headers or body, and returns a {@link User} instance with filled
	 * userName and userPassword
	 * 
	 * @param legacyIAMProvider
	 * 
	 * @param request           - ForgeRock HTTP {@link Request}
	 * @throws Exception
	 * 
	 */
	public static JsonValue getUserCredentials(LegacyOpenSSOProvider legacyIAMProvider, Request request) {
		try {
			return legacyIAMProvider.getUserCredentials(request);
		} catch (Exception e) {
			LOGGER.error("ForgeRockProvider::getUserCredentials > Error reading user's credentials: ", e);
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
	 * @param authorizationToken             - The authorization token
	 * @param httpClientHandler              - The ForgeRock HTTP client handler
	 * @return - true if user is migrated, false otherwise
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 */
	public static boolean userMigrated(String getUserMigrationStatusEndpoint, String userName,
			String authorizationToken, HttpClientHandler httpClientHandler)
			throws IOException, NeverThrowsException, InterruptedException {
		String encodedQuery = MessageFormat.format(QUERY, userName);
		String getUserPathWithQuery = getUserMigrationStatusEndpoint + encodedQuery;
		LOGGER.debug("ForgeRockProvider::userMigrated > Calling endpoint: {}", getUserPathWithQuery);
		Request request = new Request();
		try {
			request.setMethod(GET).setUri(getUserPathWithQuery);
		} catch (URISyntaxException e) {
			LOGGER.error("ForgeRockProvider::userMigrated > URISyntaxException: ", e);
		}
		request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
		request.getHeaders().add(AUTHORIZATION, authorizationToken);
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
		LOGGER.debug("ForgeRockProvider::userMigrated > response: {}", response.getEntity());
		JsonValue entity = JsonValue.json(response.getEntity().getJson());
		return entity != null && entity.get("resultCount") != null && entity.get("resultCount").asInteger() > 0;
	}

	/**
	 * 
	 * Authenticates the user in AM and retrieves the SSO token that will be set on
	 * the response
	 * 
	 * @param {@link                      User} - user object that holds credentials
	 *                                    for authentication
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
	public static String authenticateUser(JsonValue user, String openaAmAuthenticateURL, String acceptApiVersionHeader,
			String acceptApiVersionHeaderValue, String openAmCookieName, HttpClientHandler httpClientHandler)
			throws NeverThrowsException, InterruptedException, IOException {
		Response entity = getCallbacks(openaAmAuthenticateURL, acceptApiVersionHeader, acceptApiVersionHeaderValue,
				httpClientHandler);
		if (entity != null) {
			LOGGER.debug("ForgeRockProvider::authenticateUser() > response: {}", entity.getEntity().getJson());
			JsonValue callbacks = JsonValue.json(entity.getEntity().getJson());
			// Fill in the intercepted user credentials
			callbacks.get("callbacks").get(0).get("input").get(0).put("value", user.get(USERNAME).asString());
			callbacks.get("callbacks").get(1).get("input").get(0).put("value", user.get(PASSWORD).asString());

			LOGGER.debug("ForgeRockProvider::authenticateUser > callbacks: {}", callbacks);
			return getCookie(openaAmAuthenticateURL, callbacks, acceptApiVersionHeader, acceptApiVersionHeaderValue,
					openAmCookieName, httpClientHandler);
		}
		return null;
	}

	/**
	 * 
	 * Gets the callbacks from first authenticate request in OpenAM
	 * 
	 * @param openaAmAuthenticateURL-     the authentication URL for OpenAM
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
			request.setMethod(POST).setUri(openaAmAuthenticateURL);
		} catch (URISyntaxException e) {
			LOGGER.error("ForgeRockProvider::getCallbacks > URISyntaxException: ", e);
		}
		request.getHeaders().add(acceptApiVersionHeader, acceptApiVersionHeaderValue);
		request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
		Client client = new Client(httpClientHandler);
		return client.send(request).getOrThrow();
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
			throws NeverThrowsException, InterruptedException {
		Request request = new Request();
		try {
			request.setMethod(POST).setUri(openaAmAuthenticateURL);
		} catch (URISyntaxException e) {
			LOGGER.error("ForgeRockProvider::getCookie > URISyntaxException: ", e);
		}
		request.setEntity(callbacks);
		// clearing headers - setting entity on the request adds automatically
		// content-length which can cause problems in some legacy systems
		request.getHeaders().clear();
		request.getHeaders().add(acceptApiVersionHeader, acceptApiVersionHeaderValue);
		request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
		Client client = new Client(httpClientHandler);
		Response response = client.send(request).getOrThrow();

		if (response != null) {
			LOGGER.debug("ForgeRockProvider::getCookie > response status: {}", response.getStatus());
			Headers responseHeaders = response.getHeaders();
			Map<String, List<String>> headersMap = responseHeaders.copyAsMultiMapOfStrings();
			List<String> cookies = headersMap.get("Set-Cookie");
			String cookie = cookies.stream().filter(x -> x.contains(openAmCookieName)).findFirst().orElse(null);
			LOGGER.debug("ForgeRockProvider::getCookie > Cookie: {}", cookie);
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
	 * @param provisionUserEndpoint - The IDM create user URL
	 * @param httpClientHandler     - The ForgeRock HTTP client handler
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 * 
	 */
	public static void provisionUser(JsonValue user, String authorizationToken, String provisionUserEndpoint,
			HttpClientHandler httpClientHandler) throws NeverThrowsException, InterruptedException {
		LOGGER.debug("ForgeRockProvider::provisionUser > Start");
		Request request = new Request();
		try {
			request.setMethod(POST).setUri(provisionUserEndpoint);
		} catch (URISyntaxException e) {
			LOGGER.error("ForgeRockProvider::provisionUser > URISyntaxException: ", e);
		}
		request.setEntity(user);
		request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
		request.getHeaders().add(AUTHORIZATION, authorizationToken);
		Client client = new Client(httpClientHandler);
		Response response = client.send(request).getOrThrow();
		LOGGER.debug("ForgeRockProvider::provisionUser > response status: {}", response.getStatus());
	}

	/**
	 * 
	 * Creates a user object with the available user profile attributes
	 * 
	 * @param response             - The ForgeRock HTTP {@link Response}
	 * @param user                 - The user before getting the extended user
	 *                             profile attributes
	 * @param legacyIAMProvider
	 * @param getUserProfileMethod - Method name from the modernize library
	 * @return - Aa user object with userName userPassword, and given attributes
	 */
	public static JsonValue getExtendedUserProfile(Response response, JsonValue user,
			LegacyOpenSSOProvider legacyIAMProvider, Map<String, Object> userMappingAttributes) {
		return legacyIAMProvider.getExtendedUserAttributes(response, user.get(USERNAME).asString(),
				userMappingAttributes);
	}

	/**
	 * 
	 * Validate if authentication in Legacy IAM was successful
	 * 
	 * @param response          - The ForgeRock HTTP {@link Response}
	 * @param legacyIAMProvider
	 * @return
	 */
	public static boolean successfulLegacyAuthentication(Response response, LegacyOpenSSOProvider legacyIAMProvider) {
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
