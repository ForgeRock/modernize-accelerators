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
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Headers;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.modernize.LegacyIAMProvider;
import org.forgerock.openig.modernize.impl.LegacyOpenSSOProvider;
import org.forgerock.util.AsyncFunction;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
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
	 * request headers or body, and returns a JsonValue user instance with filled
	 * userName and userPassword attributes
	 *
	 * @param legacyIAMProvider - The service provider {@link LegacyIAMProvider}
	 * @param request           - ForgeRock HTTP {@link Request}
	 * @return - user described by a JsonValue instance containing the username and password
	 */
	public static JsonValue getUserCredentials(LegacyIAMProvider legacyIAMProvider, Request request) {
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
	 * @param getUserMigrationStatusEndpoint - the IDM end-point where we check if
	 *                                        	the user is migrated
	 * @param userName                       - the userName of the current
	 *                                        	requesting user
	 * @param authorizationToken             - the authorization token
	 * @param httpClientHandler              - the ForgeRock HTTP client handler
	 * @return - true if user is migrated, false otherwise
	 */
	public static Promise<Boolean, NeverThrowsException> userMigrated(String getUserMigrationStatusEndpoint,
			String userName, String authorizationToken, Handler httpClientHandler) {
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
		return client.send(request).thenAsync(userMigrated());
	}

	/**
	 *
	 * Async method that returns, when available, the response on whether or not
	 * the user is already migrated based on the caller's response
	 *
	 * @return - a promise of a boolean telling whether or not the user is migrated
	 */
	private static AsyncFunction<Response, Boolean, NeverThrowsException> userMigrated() {
		return response -> {
			LOGGER.error("ForgeRockProvider::userMigrated > response: {}", response.getEntity());
			JsonValue entity;
			try {
				entity = JsonValue.json(response.getEntity().getJson());
				if (entity.isNotNull() && entity.isDefined("resultCount") && entity.get("resultCount").asInteger() > 0) {
					return Promises.newResultPromise(true);
				}
			} catch (IOException e) {
				LOGGER.error("ForgeRockProvider::userMigrated > IOException: {0}", e);
			}

			return Promises.newResultPromise(false);
		};
	}

	/**
	 * 
	 * Authenticates the user in AM and retrieves the SSO token that will be set on
	 * the response
	 * 
	 * @param user 						  - user object that holds credentials for authentication
	 * @param openAmAuthenticateURL       - the authentication URL for OpenAM
	 * @param acceptApiVersionHeader      - Accept API Version header name
	 * @param acceptApiVersionHeaderValue - Accept API version header value
	 * @param httpClientHandler           - The ForgeRock HTTP client handler
	 * @return - the promise of a response as the result of the authentication which contains the session cookie
	 */
	public static Promise<Response, NeverThrowsException> authenticateUser(JsonValue user, String openAmAuthenticateURL,
			String acceptApiVersionHeader, String acceptApiVersionHeaderValue, Handler httpClientHandler) {

		Promise<Response, NeverThrowsException> entity = getCallbacks(openAmAuthenticateURL, acceptApiVersionHeader,
				acceptApiVersionHeaderValue, httpClientHandler);

		return entity.thenAsync(getAuthResponse(openAmAuthenticateURL, acceptApiVersionHeader, acceptApiVersionHeaderValue,
				user, httpClientHandler));
	}

	/**
	 * 
	 * Gets the callbacks from first authenticate request in OpenAM
	 * 
	 * @param openAmAuthenticateURL		  - the authentication URL for OpenAM
	 * @param acceptApiVersionHeader      - Accept API Version header name
	 * @param acceptApiVersionHeaderValue - Accept API version header value
	 * @param httpClientHandler           - the ForgeRock HTTP client handler
	 * @return - response containing authentication callbacks
	 */
	private static Promise<Response, NeverThrowsException> getCallbacks(String openAmAuthenticateURL,
			String acceptApiVersionHeader, String acceptApiVersionHeaderValue, Handler httpClientHandler) {

		Request request = new Request();
		try {
			request.setMethod(POST).setUri(openAmAuthenticateURL);
		} catch (URISyntaxException e) {
			LOGGER.error("ForgeRockProvider::getCallbacks > URISyntaxException: ", e);
		}
		request.getHeaders().add(acceptApiVersionHeader, acceptApiVersionHeaderValue);
		request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
		Client client = new Client(httpClientHandler);
		return client.send(request);
	}

	/**
	 * 
	 * Sends the authentication request callbacks with credentials to OpenAM and
	 * extracts the SSO token from the authentication response headers
	 * 
	 * @param openAmAuthenticateURL       - the authentication URL for OpenA
	 * @param acceptApiVersionHeader      - Accept API Version header name
	 * @param acceptApiVersionHeaderValue - Accept API version header value
	 * @param httpClientHandler           - the ForgeRock HTTP client handler
	 * @return - the SSO token received following the authentication
	 * 
	 */
	private static AsyncFunction<Response, Response, NeverThrowsException> getAuthResponse(String openAmAuthenticateURL,
			String acceptApiVersionHeader, String acceptApiVersionHeaderValue, JsonValue user, Handler httpClientHandler) {

		return response -> {
			try (Request request = new Request()) {
				request.setMethod(POST).setUri(openAmAuthenticateURL);

				// clearing headers - setting entity on the request adds automatically
				// content-length which can cause problems in some legacy systems
				request.getHeaders().clear();
				request.getHeaders().add(acceptApiVersionHeader, acceptApiVersionHeaderValue);
				request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);

				JsonValue callbacks = extractCredentials(user, JsonValue.json(response.getEntity().getJson()));
				request.setEntity(callbacks);

				Client client = new Client(httpClientHandler);
				return client.send(request);

			} catch (URISyntaxException | IOException e) {
				LOGGER.error("ForgeRockProvider::getCookie > {}: {} ", e.getMessage(), e);
			}

			return Promises.newResultPromise(null);
		};
	}

	/**
	 * Extracts user's cookie from the response entity
	 *
	 * @param response			- response from where to extract the cookie from
	 * @param openAmCookieName  - cookie's name on the entity's json
	 * @return - the cookie found on the response; null if absent
	 */
	public static String extractCookie(Response response, String openAmCookieName) {

		Headers responseHeaders = response.getHeaders();
		Map<String, List<String>> headersMap = responseHeaders.copyAsMultiMapOfStrings();
		List<String> cookies = headersMap.get("Set-Cookie");
		String cookie = cookies.stream().filter(x -> x.contains(openAmCookieName)).findFirst().orElse(null);
		LOGGER.debug("ForgeRockProvider::getCookie > Cookie: {}", cookie);
		return cookie;
	}

	/**
	 * Extracts user's credentials from the callback's response entity
	 *
	 * @param user		- the user
	 * @param callbacks - callback's response
	 * @return - the user's credentials or null if absent
	 */
	private static JsonValue extractCredentials(JsonValue user, JsonValue callbacks) {
		// Fill in the intercepted user credentials

		callbacks.get("callbacks").get(0).get("input").get(0).put("value", user.get(USERNAME).asString());
		callbacks.get("callbacks").get(1).get("input").get(0).put("value", user.get(PASSWORD).asString());

		LOGGER.info("ForgeRockProvider::authenticateUser > callbacks: {}", callbacks);
		return callbacks;
	}

	/**
	 * 
	 * Calls IDM user URL in order to create a user
	 * 
	 * @param user                  - An user object with userName, userPassword set,
	 *                               and extended user profile attributes
	 * @param provisionUserEndpoint - The IDM create user URL
	 * @param httpClientHandler     - The ForgeRock HTTP client handler
	 * @return - Promise of a response as the result of the user creation request in IDM
	 */
	public static Promise<Response, NeverThrowsException> provisionUser(JsonValue user, String authorizationToken,
			String provisionUserEndpoint, Handler httpClientHandler) {
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
		return client.send(request);
	}

	/**
	 * 
	 * Creates a user object with the available user profile attributes
	 * 
	 * @param response             	- The ForgeRock HTTP {@link Response}
	 * @param user                 	- The user before getting the extended user profile attributes
	 * @param legacyIAMProvider	   	- The service provider {@link LegacyIAMProvider}
	 * @param userMappingAttributes - Attribute mapping for the IDM object
	 * @param httpClientHandler 	- Used handler for the communication over HTTP
	 * @return - A user object with userName userPassword, and given attributes
	 */
	public static Promise<Response, NeverThrowsException> getExtendedUserProfile(Response response, JsonValue user,
			 LegacyOpenSSOProvider legacyIAMProvider, Map<String, Object> userMappingAttributes, Handler httpClientHandler) {
		return legacyIAMProvider.getExtendedUserAttributes(response, user.get(USERNAME).asString(),
				userMappingAttributes, httpClientHandler);
	}

	/**
	 * 
	 * Validates if the authentication in Legacy IAM was successful based on the given response
	 * 
	 * @param response          - The ForgeRock HTTP {@link Response}
	 * @param legacyIAMProvider	- The service provider {@link LegacyIAMProvider}
	 * @return - Whether or not the user was successfully authenticated
	 */
	public static boolean successfulLegacyAuthentication(Response response, LegacyOpenSSOProvider legacyIAMProvider) {
		return legacyIAMProvider.validateLegacyAuthResponse(response);
	}

	/**
	 * 
	 * Creates the error message response in case something failed during the
	 * authentication/migration process
	 * 
	 * @return - response body formatter
	 */
	public static JsonValue createFailedLoginError() {
		return JsonValue.json(JsonValue.object(JsonValue.field("code", 401), JsonValue.field("reason", "Unauthorized"),
				JsonValue.field("message", "Authentication Failed")));
	}
}
