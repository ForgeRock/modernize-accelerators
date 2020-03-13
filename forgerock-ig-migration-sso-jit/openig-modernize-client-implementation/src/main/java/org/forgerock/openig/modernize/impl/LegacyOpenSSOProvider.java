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
package org.forgerock.openig.modernize.impl;

import static org.forgerock.util.Options.defaultOptions;

import java.io.IOException;
import java.net.URISyntaxException;
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
import org.forgerock.openig.modernize.common.User;
import org.forgerock.util.promise.NeverThrowsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegacyOpenSSOProvider implements LegacyIAMProvider {

	private Logger LOGGER = LoggerFactory.getLogger(LegacyOpenSSOProvider.class);
	private static ResourceBundle rb = ResourceBundle.getBundle("config");

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
	public User getUserCredentials(Request request) throws Exception {
		LOGGER.debug("getUserCredentials()::Start");
		JsonValue entity = JsonValue.json(request.getEntity().getJson());
		if (entity != null) {
			User user = new User();
			user.setUserName(entity.get(rb.getString(CONFIG_CALLBACKS)).get(0).get(rb.getString(CONFIG_CALLBACKS_INPUT))
					.get(0).get(rb.getString(CONFIG_CALLBACKS_INPUT_VALUE)).asString());
			user.setUserPassword(
					entity.get(rb.getString(CONFIG_CALLBACKS)).get(1).get(rb.getString(CONFIG_CALLBACKS_INPUT)).get(0)
							.get(rb.getString(CONFIG_CALLBACKS_INPUT_VALUE)).asString());
			LOGGER.debug("getUserCredentials()::user: " + user.toString());
			return user;
		}
		LOGGER.error(
				"getUserCredentials()::End: Something went wrong while intercepting and reading the user credentials.");
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public User getExtendedUserAttributes(Response response, String userName) {
		String legacyCookie = getLegacyCookie(response.getHeaders().copyAsMultiMapOfStrings(),
				rb.getString(CONFIG_LEGACY_COOKIE_NAME));
		try {
			return getExtendedUserProfile(userName, legacyCookie);
		} catch (NeverThrowsException e) {
			LOGGER.debug("getExtendedUserAttributes()::NeverThrowsException: " + e);
		} catch (InterruptedException e) {
			LOGGER.debug("getExtendedUserAttributes()::InterruptedException: " + e);
		} catch (HttpApplicationException e) {
			LOGGER.debug("getExtendedUserAttributes()::HttpApplicationException: " + e);
		} catch (IOException e) {
			LOGGER.debug("getExtendedUserAttributes()::IOException: " + e);
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean validateLegacyAuthResponse(Response response) {
		LOGGER.debug("validateLegacyAuthResponse()::response.getStatus(): " + response.getStatus());
		if (response.getStatus().equals(Status.OK)) {
			if (getLegacyCookie(response.getHeaders().copyAsMultiMapOfStrings(),
					rb.getString(CONFIG_LEGACY_COOKIE_NAME)) != null) {
				LOGGER.debug("validateLegacyAuthResponse()::Success");
				return true;
			}
		}
		LOGGER.debug("validateLegacyAuthResponse()::Fail");
		return false;
	}

	/**
	 * 
	 * Retrieves the legacy cookie from the reponse headers.
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
	 * Retreieves the user profile attributes from the legacy IAM platform.
	 * 
	 * @param username
	 * @param cookie
	 * @return
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 * @throws HttpApplicationException
	 * @throws IOException
	 */
	private User getExtendedUserProfile(String username, String cookie)
			throws NeverThrowsException, InterruptedException, HttpApplicationException, IOException {
		StringBuilder legacygetUserDetailsEndpoint = new StringBuilder();
		legacygetUserDetailsEndpoint.append(rb.getString(CONFIG_USER_DETAILS_URL)).append(username);
		Response responseEntity = callUserDetailsEndpoint(legacygetUserDetailsEndpoint.toString(), cookie);
		if (responseEntity != null) {
			return setUserProperties(responseEntity);
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
			request.setMethod("GET").setUri(legacygetUserDetailsEndpoint);
		} catch (URISyntaxException e) {
			LOGGER.error("getuser()::URISyntaxException: " + e);
		}
		request.getHeaders().add(rb.getString(CONFIG_COOKIE_HEADER), cookie);
		request.getHeaders().add("Content-Type", "application/json");
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
	private User setUserProperties(Response responseEntity) throws IOException {
		JsonValue entity = JsonValue.json(responseEntity.getEntity().getJson());
		if (entity != null) {
			User user = new User();
			user.setUserFirstName(entity.get("givenName").get(0).asString());
			user.setUserLastName(entity.get("sn").get(0).asString());
			user.setUserEmail(entity.get("mail").get(0).asString());
			return user;
		}
		return null;
	}
}