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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.modernize.LegacyIAMProvider;
import org.forgerock.openig.modernize.common.RequestUtils;
import org.forgerock.openig.modernize.common.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

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
		LOGGER.info("getUserCredentials()::Start");
		ObjectMapper mapper = new ObjectMapper();
		JsonNode callbacks = null;
		callbacks = mapper.readTree(request.getEntity().getString());
		if (callbacks != null) {
			User user = new User();
			user.setUserName(
					callbacks.get(rb.getString(CONFIG_CALLBACKS)).get(0).get(rb.getString(CONFIG_CALLBACKS_INPUT))
							.get(0).get(rb.getString(CONFIG_CALLBACKS_INPUT_VALUE)).asText());
			user.setUserPassword(
					callbacks.get(rb.getString(CONFIG_CALLBACKS)).get(1).get(rb.getString(CONFIG_CALLBACKS_INPUT))
							.get(0).get(rb.getString(CONFIG_CALLBACKS_INPUT_VALUE)).asText());
			LOGGER.info("getUserCredentials()::user: " + user.toString());
			return user;
		}
		LOGGER.error("getUserCredentials()::End: Something went wrong.");
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public User getExtendedUserAttributes(Response response, String userName) {
		String legacyCookie = getLegacyCookie(response.getHeaders().copyAsMultiMapOfStrings(),
				rb.getString(CONFIG_LEGACY_COOKIE_NAME));
		return getExtendedUserProfile(userName, legacyCookie);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean validateLegacyAuthResponse(Response response) {
		LOGGER.info("validateLegacyAuthResponse()::response.getStatus(): " + response.getStatus());
		if (response.getStatus().equals(Status.OK)) {
			if (getLegacyCookie(response.getHeaders().copyAsMultiMapOfStrings(),
					rb.getString(CONFIG_LEGACY_COOKIE_NAME)) != null) {
				LOGGER.info("validateLegacyAuthResponse()::Success");
				return true;
			}
		}
		LOGGER.info("validateLegacyAuthResponse()::Fail");
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
	 */
	private User getExtendedUserProfile(String username, String cookie) {
		StringBuilder legacygetUserDetailsEndpoint = new StringBuilder();
		legacygetUserDetailsEndpoint.append(rb.getString(CONFIG_USER_DETAILS_URL)).append(username);
		ResponseEntity<String> responseEntity = callUserDetailsEndpoint(legacygetUserDetailsEndpoint.toString(),
				cookie);
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
	 */
	private ResponseEntity<String> callUserDetailsEndpoint(String legacygetUserDetailsEndpoint, String cookie) {
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<String, String>();
		headersMap.add(rb.getString(CONFIG_COOKIE_HEADER), cookie);
		return RequestUtils.sendGetRequest(legacygetUserDetailsEndpoint, MediaType.APPLICATION_JSON, headersMap);
	}

	/**
	 * 
	 * Creates the {@link User} object that will be provisioned into the IDM
	 * platform.
	 * 
	 * @param responseEntity
	 * @return
	 */
	private User setUserProperties(ResponseEntity<String> responseEntity) {
		JsonNode node = null;
		try {
			ObjectMapper mapper = new ObjectMapper();
			node = mapper.readTree(responseEntity.getBody());
		} catch (IOException e) {
			LOGGER.error("setUserProperties()::Error: " + e.getMessage());
			e.printStackTrace();
		}
		if (node != null) {
			User user = new User();
			user.setUserFirstName(getUserProperty(node, "givenName"));
			user.setUserLastName(getUserProperty(node, "sn"));
			user.setUserEmail(getUserProperty(node, "mail"));
			return user;
		}
		return null;
	}

	String getUserProperty(JsonNode node, String propertyName) {
		ArrayNode arrayNode = (ArrayNode) node.get(propertyName);
		if (arrayNode != null) {
			return arrayNode.get(0).asText();
		}
		return null;
	}

}