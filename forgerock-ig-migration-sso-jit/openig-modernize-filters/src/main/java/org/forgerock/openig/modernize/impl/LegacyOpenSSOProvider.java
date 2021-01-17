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
package org.forgerock.openig.modernize.impl;

import static org.forgerock.openig.modernize.utils.FilterConstants.Attributes.PASSWORD;
import static org.forgerock.openig.modernize.utils.FilterConstants.Attributes.USERNAME;
import static org.forgerock.openig.modernize.utils.FilterConstants.Headers.APPLICATION_JSON;
import static org.forgerock.openig.modernize.utils.FilterConstants.Headers.CONTENT_TYPE;
import static org.forgerock.openig.modernize.utils.FilterConstants.Methods.GET;
import static org.forgerock.util.Options.defaultOptions;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.forgerock.http.Client;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openig.modernize.LegacyIAMProvider;
import org.forgerock.util.promise.NeverThrowsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegacyOpenSSOProvider implements LegacyIAMProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(LegacyOpenSSOProvider.class);
	private static ResourceBundle rb = ResourceBundle
			.getBundle("org.forgerock.openig.modernize.impl.LegacyOpenSSOProvider");

	private static final String CONFIG_CALLBACKS = "callbacksParam";
	private static final String CONFIG_CALLBACKS_INPUT = "configCallbacksInputParam";
	private static final String CONFIG_CALLBACKS_INPUT_VALUE = "configCallbacksInputValueParam";
	private static final String CONFIG_SET_COOKIE_HEADER = "setCookieHeader";
	private static final String CONFIG_USER_DETAILS_URL = "legacygetUserDetailsEndpoint";
	private static final String CONFIG_LEGACY_COOKIE_NAME = "legacyIamCookieName";
	private static final String CONFIG_COOKIE_HEADER = "cookieHeader";

	/**
	 * {@inheritDoc}
	 */
	public JsonValue getUserCredentials(Request request) throws Exception {
		LOGGER.debug("LegacyOpenSSOProvider::getUserCredentials > Start");
		JsonValue entity = JsonValue.json(request.getEntity().getJson());
		LOGGER.debug("LegacyOpenSSOProvider::getUserCredentials > Entity: {}", entity);
		JsonValue userCredentials = JsonValue.json(JsonValue.object());
		if (entity != null) {
			userCredentials.add(USERNAME,
					entity.get(rb.getString(CONFIG_CALLBACKS)).get(0).get(rb.getString(CONFIG_CALLBACKS_INPUT)).get(0)
							.get(rb.getString(CONFIG_CALLBACKS_INPUT_VALUE)).asString());
			userCredentials.add(PASSWORD,
					entity.get(rb.getString(CONFIG_CALLBACKS)).get(1).get(rb.getString(CONFIG_CALLBACKS_INPUT)).get(0)
							.get(rb.getString(CONFIG_CALLBACKS_INPUT_VALUE)).asString());

			return userCredentials;
		}
		LOGGER.error(
				"LegacyOpenSSOProvider::getUserCredentials > Something went wrong while intercepting and reading the user credentials.");
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public JsonValue getExtendedUserAttributes(Response response, String userName,
			Map<String, Object> userAttributesMapping) {
		String legacyCookie = getLegacyCookie(response.getHeaders().copyAsMultiMapOfStrings(),
				rb.getString(CONFIG_LEGACY_COOKIE_NAME));
		LOGGER.debug("LegacyOpenSSOProvider::getExtendedUserAttributes > legacyCookie: {}", legacyCookie);
		try {
			return getExtendedUserProfile(userName, legacyCookie, userAttributesMapping);
		} catch (NeverThrowsException e) {
			LOGGER.error("LegacyOpenSSOProvider::getExtendedUserAttributes > NeverThrowsException: ", e);
		} catch (InterruptedException e) {
			LOGGER.error("LegacyOpenSSOProvider::getExtendedUserAttributes > InterruptedException: ", e);
			Thread.currentThread().interrupt();
		} catch (HttpApplicationException e) {
			LOGGER.error("LegacyOpenSSOProvider::getExtendedUserAttributes > HttpApplicationException: ", e);
		} catch (IOException e) {
			LOGGER.error("LegacyOpenSSOProvider::getExtendedUserAttributes > IOException: ", e);
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
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
	 * @param responseHeadersMap
	 * @param legacyIamCookieName
	 * @return
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
	 * @param username
	 * @param cookie
	 * @param userAttributesMapping - the mapping of attributes as they are in
	 *                              legacy and how they are in IDM
	 * @return
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 * @throws HttpApplicationException
	 * @throws IOException
	 */
	private JsonValue getExtendedUserProfile(String username, String cookie, Map<String, Object> userAttributesMapping)
			throws NeverThrowsException, InterruptedException, HttpApplicationException, IOException {
		StringBuilder legacygetUserDetailsEndpoint = new StringBuilder();
		legacygetUserDetailsEndpoint.append(rb.getString(CONFIG_USER_DETAILS_URL)).append(username);
		LOGGER.debug("LegacyOpenSSOProvider::getExtendedUserProfile > legacygetUserDetailsEndpoint: {}",
				legacygetUserDetailsEndpoint);
		Response responseEntity = callUserDetailsEndpoint(legacygetUserDetailsEndpoint.toString(), cookie);
		if (responseEntity != null) {
			return setUserProperties(responseEntity, userAttributesMapping);
		}
		return null;
	}

	/**
	 * 
	 * Calls the user profile details API from the legacy IAM platform.
	 * 
	 * @param legacygetUserDetailsEndpoint
	 * @param cookie
	 * @return
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 * @throws HttpApplicationException
	 */
	private Response callUserDetailsEndpoint(String legacygetUserDetailsEndpoint, String cookie)
			throws NeverThrowsException, InterruptedException, HttpApplicationException {
		HttpClientHandler httpClientHandler = new HttpClientHandler(defaultOptions());
		Request request = new Request();
		try {
			request.setMethod(GET).setUri(legacygetUserDetailsEndpoint);
		} catch (URISyntaxException e) {
			LOGGER.error("LegacyOpenSSOProvider::getuser > URISyntaxException: ", e);
		}
		request.getHeaders().add(rb.getString(CONFIG_COOKIE_HEADER), cookie);
		request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
		Client client = new Client(httpClientHandler);
		return client.send(request).getOrThrow();
	}

	/**
	 * 
	 * Creates the {@link User} object that will be provisioned into the IDM
	 * platform.
	 * 
	 * @param responseEntity
	 * @return
	 * @throws IOException
	 */
	private JsonValue setUserProperties(Response responseEntity, Map<String, Object> userAttributesMapping)
			throws IOException {
		LOGGER.error("LegacyOpenSSOProvider::setUserProperties > responseEntity: {}", responseEntity.getEntity());
		JsonValue entity = JsonValue.json(responseEntity.getEntity().getJson());

		JsonValue userAttributes = JsonValue.json(JsonValue.object());

		if (entity != null) {
			Iterator<Map.Entry<String, Object>> itr = userAttributesMapping.entrySet().iterator();
			LOGGER.error("LegacyOpenSSOProvider::setUserProperties > userAttributesMapping: {}", userAttributesMapping);

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
		}
		return null;
	}
}