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
package org.forgerock.openig.modernize.impl;

import static org.forgerock.openig.modernize.utils.FilterConstants.Attributes.PASSWORD;
import static org.forgerock.openig.modernize.utils.FilterConstants.Attributes.USERNAME;
import static org.forgerock.openig.modernize.utils.FilterConstants.Headers.APPLICATION_JSON;
import static org.forgerock.openig.modernize.utils.FilterConstants.Headers.CONTENT_TYPE;
import static org.forgerock.openig.modernize.utils.FilterConstants.Methods.GET;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.forgerock.http.Client;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.modernize.LegacyIAMProvider;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegacyOpenSSOProvider implements LegacyIAMProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(LegacyOpenSSOProvider.class);
	private static final ResourceBundle rb = ResourceBundle
			.getBundle("org.forgerock.openig.modernize.impl.LegacyOpenSSOProvider");

	private static final String CONFIG_CALLBACKS = "callbacksParam";
	private static final String CONFIG_CALLBACKS_INPUT = "configCallbacksInputParam";
	private static final String CONFIG_CALLBACKS_INPUT_VALUE = "configCallbacksInputValueParam";
	private static final String CONFIG_SET_COOKIE_HEADER = "setCookieHeader";
	private static final String CONFIG_USER_DETAILS_URL = "legacygetUserDetailsEndpoint";
	private static final String CONFIG_LEGACY_COOKIE_NAME = "legacyIamCookieName";
	private static final String CONFIG_COOKIE_HEADER = "cookieHeader";

	@Override
	public JsonValue getUserCredentials(Request request) {
		try {
			LOGGER.debug("LegacyOpenSSOProvider::getUserCredentials > Start");
			JsonValue entity = JsonValue.json(request.getEntity().getJson());
			LOGGER.debug("LegacyOpenSSOProvider::getUserCredentials > Entity: {}", entity);
			JsonValue userCredentials = JsonValue.json(JsonValue.object());

			userCredentials.add(USERNAME,
					entity.get(rb.getString(CONFIG_CALLBACKS)).get(0).get(rb.getString(CONFIG_CALLBACKS_INPUT)).get(0)
							.get(rb.getString(CONFIG_CALLBACKS_INPUT_VALUE)).asString());
			userCredentials.add(PASSWORD,
					entity.get(rb.getString(CONFIG_CALLBACKS)).get(1).get(rb.getString(CONFIG_CALLBACKS_INPUT)).get(0)
							.get(rb.getString(CONFIG_CALLBACKS_INPUT_VALUE)).asString());

			return userCredentials;
		} catch (IOException e) {
			LOGGER.error(
					"LegacyOpenSSOProvider::getUserCredentials > Something went wrong while "
							+ "intercepting and reading the user credentials from the request's body. Exception: {0}",
					e);
		}
		return null;
	}

	@Override
	public Promise<Response, NeverThrowsException> getExtendedUserAttributes(Response response, String userName,
			Map<String, Object> userAttributesMapping, Handler httpClientHandler) {

		String legacyCookie = getLegacyCookie(response.getHeaders().copyAsMultiMapOfStrings(),
				rb.getString(CONFIG_LEGACY_COOKIE_NAME));
		LOGGER.debug("LegacyOpenSSOProvider::getExtendedUserAttributes > legacyCookie: {}", legacyCookie);
		return getExtendedUserProfile(userName, legacyCookie, userAttributesMapping, httpClientHandler);
	}

	@Override
	public boolean validateLegacyAuthResponse(Response response) {
		LOGGER.debug("LegacyOpenSSOProvider::validateLegacyAuthResponse > Legacy authentication response status: {}",
				response.getStatus());

		if (response.getStatus().equals(Status.OK) && getLegacyCookie(response.getHeaders().copyAsMultiMapOfStrings(),
				rb.getString(CONFIG_LEGACY_COOKIE_NAME)) != null) {
			LOGGER.debug("LegacyOpenSSOProvider::validateLegacyAuthResponse > Success!");
			return true;
		}

		LOGGER.error("validateLegacyAuthResponse()::Fail");
		return false;
	}

	/**
	 * 
	 * Retrieves the legacy cookie from the response headers.
	 * 
	 * @param responseHeadersMap  - map of the response's headers
	 * @param legacyIamCookieName - name of the claim where the cookie is found
	 * @return - the extracted cookie from the given header map
	 */
	private String getLegacyCookie(Map<String, List<String>> responseHeadersMap, String legacyIamCookieName) {
		List<String> cookieValues = responseHeadersMap.get(rb.getString(CONFIG_SET_COOKIE_HEADER));
		for (String cookie : cookieValues) {
			if (cookie != null && cookie.contains(legacyIamCookieName)) {
				return cookie;
			}
		}
		return null;
	}

	/**
	 * 
	 * Retrieves the user profile attributes from the legacy IAM platform.
	 * 
	 * @param username              - username
	 * @param cookie                - user's cookie value
	 * @param userAttributesMapping - the mapping of attributes as they are in
	 *                              legacy and how they are in IDM
	 * @return - JsonValue containing the user's extended attributes
	 */
	private Promise<Response, NeverThrowsException> getExtendedUserProfile(String username, String cookie,
			Map<String, Object> userAttributesMapping, Handler httpClientHandler) {

		StringBuilder legacyGetUserDetailsEndpoint = new StringBuilder();
		legacyGetUserDetailsEndpoint.append(rb.getString(CONFIG_USER_DETAILS_URL)).append(username);
		return callUserDetailsEndpoint(legacyGetUserDetailsEndpoint.toString(), cookie, httpClientHandler);

	}

	/**
	 * 
	 * Calls the user profile details API from the legacy IAM platform.
	 * 
	 * @param legacyGetUserDetailsEndpoint - legacy user details endpoint
	 * @param cookie                       - user's cookie
	 * @return - the promise of a response containing the user's details fetched
	 *         from the AM portal
	 */
	private Promise<Response, NeverThrowsException> callUserDetailsEndpoint(String legacyGetUserDetailsEndpoint,
			String cookie, Handler httpClientHandler) {
		Request request = new Request();
		try {
			request.setMethod(GET).setUri(legacyGetUserDetailsEndpoint);
		} catch (URISyntaxException e) {
			LOGGER.error("LegacyOpenSSOProvider::getuser > URISyntaxException: ", e);
		}
		request.getHeaders().add(rb.getString(CONFIG_COOKIE_HEADER), cookie);
		request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);

		Client client = new Client(httpClientHandler);
		return client.send(request);
	}
}