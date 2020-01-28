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
package org.forgerock.openam.auth.node;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.PASSWORD;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.treehook.LegacySessionTreeHook;
import org.forgerock.openam.modernize.utils.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

/**
 * 
 * <p>
 * This node attempts to authenticate in the legacy iAM and retrieve an SSO
 * token
 * </p>
 *
 */
@Node.Metadata(configClass = LegacyFRLogin.Config.class, outcomeProvider = AbstractDecisionNode.OutcomeProvider.class)
public class LegacyFRLogin extends AbstractDecisionNode {

	private static final String DEFAULT_LEGACY_COOKIE_NAME = "legacyCookieName";
	private Logger LOGGER = LoggerFactory.getLogger(LegacyFRLogin.class);
	private final Config config;
	private final UUID nodeId;

	public interface Config {

		@Attribute(order = 1, validators = { RequiredValueValidator.class })
		String legacyLoginUri();

		@Attribute(order = 2, validators = { RequiredValueValidator.class })
		default String legacyCookieName() {
			return DEFAULT_LEGACY_COOKIE_NAME;
		};

	}

	@Inject
	public LegacyFRLogin(@Assisted LegacyFRLogin.Config config, @Assisted UUID nodeId) {
		this.config = config;
		this.nodeId = nodeId;
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String username = context.sharedState.get(USERNAME).asString();
		String password = context.transientState.get(PASSWORD).asString();

		String callback = getCallbacks(config.legacyLoginUri());
		String responseCookie = null;
		try {
			if (callback != null && !callback.isEmpty()) {
				String callbackBody = createAuthenticationCallbacks(callback, username, password);
				responseCookie = getCookie(config.legacyLoginUri(), callbackBody);
			}
		} catch (IOException e) {
			LOGGER.error("process()::IOException: " + e.getMessage());
			throw new NodeProcessException(e);
		}

		if (responseCookie != null) {
			LOGGER.info("process(): Successfull login in legacy system.");
			return goTo(true).putSessionProperty(LEGACY_COOKIE_SHARED_STATE_PARAM, responseCookie)
					.addSessionHook(LegacySessionTreeHook.class, nodeId, getClass().getSimpleName())
					.replaceSharedState(context.sharedState.put(LEGACY_COOKIE_SHARED_STATE_PARAM, responseCookie))
					.build();
		} else {
			return goTo(false).build();
		}
	}

	/**
	 * Initializes communication with the legacy iAM requesting the authentication
	 * callbacks.
	 * 
	 * @param url
	 * @return the authentication callbacks
	 */
	private String getCallbacks(String url) {
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add("Accept-API-Version", "resource=2.0, protocol=1.0");
		ResponseEntity<String> responseEntity = RequestUtils.sendPostRequest(url, null, MediaType.APPLICATION_JSON,
				headersMap);
		LOGGER.debug("getCallbacks()::response.getBody(): " + responseEntity.getBody());
		return responseEntity.getBody();
	}

	/**
	 * 
	 * Fills the user credentials in the authentication callbacks
	 * 
	 * @param callback
	 * @param userId
	 * @param password
	 * @return the authentication callbacks with credential written
	 * @throws IOException
	 */
	private String createAuthenticationCallbacks(String callback, String userId, String password) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode callbackNode = mapper.createObjectNode();
		callbackNode = (ObjectNode) mapper.readTree(callback);
		ObjectNode nameCallback = (ObjectNode) callbackNode.get("callbacks").get(0).get("input").get(0);
		nameCallback.put("value", userId);
		ObjectNode passwordCallback = (ObjectNode) callbackNode.get("callbacks").get(1).get("input").get(0);
		passwordCallback.put("value", password);
		return callbackNode.toString();
	}

	/**
	 * 
	 * Get the SSO token from the authentication response, if present
	 * 
	 * @param url
	 * @param jsonBody
	 * @return
	 */
	private String getCookie(String url, String jsonBody) {
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add("Accept-API-Version", "resource=2.0, protocol=1.0");
		ResponseEntity<String> responseEntity = RequestUtils.sendPostRequest(url, jsonBody, MediaType.APPLICATION_JSON,
				headersMap);
		LOGGER.debug("getCookie()::response.getBody(): " + responseEntity.getBody());
		HttpHeaders responseHeaders = responseEntity.getHeaders();
		List<String> cookies = responseHeaders.get("Set-Cookie");
		String cookie = cookies.stream().filter(x -> x.contains(config.legacyCookieName())).findFirst().orElse(null);
		LOGGER.error("getCookie()::Cookie: " + cookie);
		return cookie;
	}

}