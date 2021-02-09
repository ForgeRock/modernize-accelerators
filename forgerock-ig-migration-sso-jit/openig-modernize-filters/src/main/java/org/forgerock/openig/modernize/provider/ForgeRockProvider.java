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
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.json.JsonValueException;
import org.forgerock.openig.modernize.LegacyIAMProvider;
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
		return legacyIAMProvider.getUserCredentials(request);
	}

	/**
	 * 
	 * Calls IDM to get the information needed to determine if the user is migrated
	 * 
	 * @param getUserMigrationStatusEndpoint - the IDM end-point where we check if the user is migrated
	 * @param userName                       - the userName of the current requesting user
	 * @param authorizationToken             - the authorization token
	 * @param httpClientHandler              - the ForgeRock HTTP client handler
	 * @return - true if user is migrated, false otherwise
	 */
	public static Promise<Boolean, NeverThrowsException> userMigrated(String getUserMigrationStatusEndpoint,
			String userName, String authorizationToken, Handler httpClientHandler) {

		String encodedQuery = MessageFormat.format(QUERY, userName);
		String getUserPathWithQuery = getUserMigrationStatusEndpoint + encodedQuery;
		LOGGER.info("ForgeRockProvider::userMigrated > Calling endpoint: {}", getUserPathWithQuery);

		try (Request request = new Request()) {
			request.setMethod(GET).setUri(getUserPathWithQuery);

			request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
			request.getHeaders().add(AUTHORIZATION, authorizationToken);

			Client client = new Client(httpClientHandler);
			return client.send(request).thenAsync(userMigrated());
		} catch (URISyntaxException e) {
			LOGGER.error("ForgeRockProvider::userMigrated > URISyntaxException: ", e);
		}

		return Promises.newResultPromise(false);
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

		try (Request request = new Request()) {
			request.setMethod(POST).setUri(openAmAuthenticateURL);

			request.getHeaders().add(acceptApiVersionHeader, acceptApiVersionHeaderValue);
			request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);

			Client client = new Client(httpClientHandler);
			return client.send(request);
		} catch (URISyntaxException e) {
			LOGGER.error("ForgeRockProvider::getCallbacks > URISyntaxException: ", e);
		}

		return getErrorResponse(Status.BAD_REQUEST);
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

				// Header cleanup - setting entity on the request automatically adds
				// content-length which can cause problems in some legacy systems
				request.getHeaders().clear();
				request.getHeaders().add(acceptApiVersionHeader, acceptApiVersionHeaderValue);
				request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);

				JsonValue callbacks = extractCredentials(user, JsonValue.json(response.getEntity().getJson()));
				request.setEntity(callbacks);

				Client client = new Client(httpClientHandler);
				return client.send(request);
			} catch (URISyntaxException | IOException | JsonValueException e) {
				LOGGER.error("ForgeRockProvider::getAuthResponse > {}: {} ", e.getMessage(), e);
			}

			return getErrorResponse(Status.BAD_REQUEST);
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

		LOGGER.info("ForgeRockProvider::extractCookie > Cookie: {}", cookie);
		return cookie;
	}

	/**
	 * Extracts user's credentials from the callback's response entity after
	 * checking their validity and existence
	 *
	 * @param user		- the user
	 * @param callbacks - callback's response
	 * @return - the user's credentials or null if absent
	 */
	private static JsonValue extractCredentials(JsonValue user, JsonValue callbacks) {
		final String callbacksParam = "callbacks";
		final String callbacksInputParam = "input";

		if (validCallback(callbacks)) {
			callbacks.get(callbacksParam).get(0).get(callbacksInputParam).get(0).put("value", user.get(USERNAME).asString());
			callbacks.get(callbacksParam).get(1).get(callbacksInputParam).get(0).put("value", user.get(PASSWORD).asString());

			return callbacks;
		}

		return null;
	}

	/**
	 *
	 * Util method that checks the structure of the callback json.
	 *
	 * @param callbacks - json to check against the expected format
	 * @return - true if it's format is validated; false otherwise
	 */
	public static boolean validCallback(JsonValue callbacks) {
		final String callbacksParam = "callbacks";
		final String callbacksInputParam = "input";

		if (callbacks.isDefined(callbacksParam)) {
			JsonValue usernameCallback = callbacks.get(callbacksParam).get(0);
			JsonValue passwordCallback = callbacks.get(callbacksParam).get(1);

			return usernameCallback.isDefined(callbacksInputParam) && passwordCallback.isDefined(callbacksInputParam);
		}

		return false;
	}

	/**
	 * 
	 * Calls IDM user URL in order to create a user
	 * 
	 * @param user                  - user object with userName, userPassword set, and extended user profile attributes
	 * @param authorizationToken	- string representing the authorization token used to provision the user
	 * @param provisionUserEndpoint - IDMs create user URL
	 * @param httpClientHandler     - ForgeRock HTTP client handler
	 * @return - promise of a response as the result of the user creation request in IDM
	 */
	public static Promise<Response, NeverThrowsException> provisionUser(JsonValue user, String authorizationToken,
			String provisionUserEndpoint, Handler httpClientHandler) {
		LOGGER.info("ForgeRockProvider::provisionUser > Start");

		try (Request request = new Request()) {
			request.setMethod(POST).setUri(provisionUserEndpoint);

			request.setEntity(user);

			request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
			request.getHeaders().add(AUTHORIZATION, authorizationToken);

			Client client = new Client(httpClientHandler);
			return client.send(request);
		} catch (URISyntaxException e) {
			LOGGER.error("ForgeRockProvider::provisionUser > URISyntaxException: ", e);
		}

		return getErrorResponse(Status.BAD_REQUEST);
	}

	/**
	 * 
	 * Creates a user object with the available user profile attributes
	 * 
	 * @param response             	- the ForgeRock HTTP {@link Response}
	 * @param user                 	- the user before getting the extended user profile attributes
	 * @param legacyIAMProvider	   	- the service provider {@link LegacyIAMProvider}
	 * @param httpClientHandler 	- handler for the communication over HTTP
	 * @return - user object with userName userPassword, and given attributes
	 */
	public static Promise<Response, NeverThrowsException> getExtendedUserProfile(Response response, JsonValue user,
			 LegacyIAMProvider legacyIAMProvider, Handler httpClientHandler) {
		return legacyIAMProvider.getExtendedUserAttributes(response, user.get(USERNAME).asString(),
				httpClientHandler);
	}

	/**
	 * 
	 * Creates the error message response in case something failed during the
	 * authentication/migration process
	 * 
	 * @return - response body formatter
	 */
	private static JsonValue createFailedErrorEntity(Status responseStatus) {
		String message = responseStatus.getCode() == 401 ? "Authentication failed" : "Malformed request syntax";
		return JsonValue.json(JsonValue.object(JsonValue.field("code", responseStatus.getCode()),
				JsonValue.field("reason", responseStatus.getReasonPhrase()), JsonValue.field("message", message)));
	}

	/**
	 *
	 * Creates and returns a custom client error response
	 *
	 * @return - the promise of a client error HTTP response
	 */
	public static Promise<Response, NeverThrowsException> getErrorResponse(Status responseStatus) {
		Response unauthorizedResponse = new Response(responseStatus);
		unauthorizedResponse.setEntity(ForgeRockProvider.createFailedErrorEntity(responseStatus));
		return Promises.newResultPromise(unauthorizedResponse);
	}
}
