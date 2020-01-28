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
import static org.forgerock.openam.modernize.utils.NodeConstants.DEFAULT_IDM_USER;
import static org.forgerock.openam.modernize.utils.NodeConstants.DEFAULT_IDM_USER_ENDPOINT;
import static org.forgerock.openam.modernize.utils.NodeConstants.DEFAULT_IDM_USER_PASSWORD;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_PASSWORD_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_USERNAME_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_FORCE_PASSWORD_RESET;

import javax.inject.Inject;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.modernize.utils.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

/**
 * 
 * <p>
 * This node updates a users password.
 * </p>
 *
 */
@Node.Metadata(configClass = LegacyFRSetPassword.Config.class, outcomeProvider = AbstractDecisionNode.OutcomeProvider.class)
public class LegacyFRSetPassword extends AbstractDecisionNode {

	private Logger LOGGER = LoggerFactory.getLogger(LegacyFRSetPassword.class);
	private final Config config;
	private String idmPassword;

	public interface Config {

		@Attribute(order = 1, validators = { RequiredValueValidator.class })
		default String idmUserEndpoint() {
			return DEFAULT_IDM_USER_ENDPOINT;
		};

		@Attribute(order = 2, validators = { RequiredValueValidator.class })
		default String idmAdminUser() {
			return DEFAULT_IDM_USER;
		};

		@Attribute(order = 3, validators = { RequiredValueValidator.class })
		default String idmPasswordId() {
			return DEFAULT_IDM_USER_PASSWORD;
		};
	}

	@Inject
	public LegacyFRSetPassword(@Assisted LegacyFRSetPassword.Config config) {
		this.config = config;
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String username = context.sharedState.get(USERNAME).asString();
		String password = context.transientState.get(PASSWORD).asString();
		return goTo(setUserPassword(username, password)).build();

	}

	/**
	 * 
	 * Sets the user password id DS via the IDM API
	 * 
	 * @param userName
	 * @param password
	 * @return true if successful, false otherwise
	 */
	private boolean setUserPassword(String userName, String password) {
		LOGGER.error("setUserPassword()::Start");
		String jsonBody = createPasswordRequestEntity(password);
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add(OPEN_IDM_ADMIN_USERNAME_HEADER, config.idmAdminUser());
		headersMap.add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
		ResponseEntity<String> responseStatusCode = RequestUtils.sendPostRequest(
				config.idmUserEndpoint() + "\"" + userName + "\"", jsonBody, MediaType.APPLICATION_JSON, headersMap);
		if (responseStatusCode != null) {
			if (responseStatusCode.getStatusCodeValue() == 200) {
				LOGGER.error("setUserPassword()::End - success - 200 OK");
				return true;
			}
		}
		LOGGER.error("setUserPassword()::End - fail");
		return false;
	}

	/**
	 * Creates the request body for password update, in the format accepted by IDM
	 * 
	 * @param password
	 * @return JSON string
	 */
	private String createPasswordRequestEntity(String password) {
		LOGGER.debug("createPasswordRequestEntity()::Start");
		ObjectMapper mapper = new ObjectMapper();
		ArrayNode updatesList = mapper.createArrayNode();

		ObjectNode replacePasswordNode = mapper.createObjectNode();
		replacePasswordNode.put("operation", "replace");
		replacePasswordNode.put("field", PASSWORD);
		replacePasswordNode.put("value", password);
		updatesList.add(replacePasswordNode);

		ObjectNode replacePasswordResetNode = mapper.createObjectNode();
		replacePasswordResetNode.put("operation", "replace");
		replacePasswordResetNode.put("field", USER_FORCE_PASSWORD_RESET);
		replacePasswordResetNode.put("value", false);
		updatesList.add(replacePasswordResetNode);

		String jsonString = null;
		try {
			jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(updatesList);
		} catch (JsonProcessingException e) {
			LOGGER.error("createPasswordRequestEntity()::Error creating provisioning entity: " + e.getMessage());
			e.printStackTrace();
		}
		LOGGER.debug("createPasswordRequestEntity()::entity: " + jsonString);
		return jsonString;
	}
}