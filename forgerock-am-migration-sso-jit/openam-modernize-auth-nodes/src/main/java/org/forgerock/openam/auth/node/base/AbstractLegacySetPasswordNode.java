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
package org.forgerock.openam.auth.node.base;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.PASSWORD;
import static org.forgerock.openam.modernize.utils.NodeConstants.FALSE_OUTCOME_ID;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_PASSWORD_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_USERNAME_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.PATCH_IDM_USER_PATH;
import static org.forgerock.openam.modernize.utils.NodeConstants.TRUE_OUTCOME_ID;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_FORCE_PASSWORD_RESET;

import java.util.List;
import java.util.ResourceBundle;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.modernize.utils.RequestUtils;
import org.forgerock.util.i18n.PreferredLocales;
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
import com.google.common.collect.ImmutableList;
import com.sun.identity.sm.RequiredValueValidator;

public abstract class AbstractLegacySetPasswordNode implements Node {

	private Logger LOGGER = LoggerFactory.getLogger(AbstractLegacySetPasswordNode.class);

	/**
	 * The Config.
	 */
	public interface Config {

		@Attribute(order = 1, validators = { RequiredValueValidator.class })
		String idmUserEndpoint();

		@Attribute(order = 2, validators = { RequiredValueValidator.class })
		String idmAdminUser();

		@Attribute(order = 3, validators = { RequiredValueValidator.class })
		String idmPasswordId();
	}

	/**
	 * 
	 * Sets the user password id DS via the IDM API
	 * 
	 * @param userName
	 * @param password
	 * @return true if successful, false otherwise
	 */
	protected boolean setUserPassword(String userName, String password, String idmEndpoint, String idmAdmin,
			String idmPassword) {
		LOGGER.error("setUserPassword()::Start");
		String jsonBody = createPasswordRequestEntity(password);
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add(OPEN_IDM_ADMIN_USERNAME_HEADER, idmAdmin);
		headersMap.add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
		ResponseEntity<String> responseStatusCode = RequestUtils.sendPostRequest(
				idmEndpoint + PATCH_IDM_USER_PATH + "'" + userName + "'", jsonBody, MediaType.APPLICATION_JSON,
				headersMap);
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

	/**
	 * Move on to the next node in the tree that is connected to the given outcome.
	 * 
	 * @param outcome the outcome.
	 * @return an action builder to provide additional details.
	 */
	protected Action.ActionBuilder goTo(boolean outcome) {
		return Action.goTo(outcome ? TRUE_OUTCOME_ID : FALSE_OUTCOME_ID);
	}

	/**
	 * Provides a static set of outcomes for decision nodes.
	 */
	public static final class OutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
		@Override
		public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
			ResourceBundle bundle = locales.getBundleInPreferredLocale("amAuthTrees",
					OutcomeProvider.class.getClassLoader());
			return ImmutableList.of(new Outcome(TRUE_OUTCOME_ID, bundle.getString("trueOutcome")),
					new Outcome(FALSE_OUTCOME_ID, bundle.getString("falseOutcome")));
		}
	}
}
