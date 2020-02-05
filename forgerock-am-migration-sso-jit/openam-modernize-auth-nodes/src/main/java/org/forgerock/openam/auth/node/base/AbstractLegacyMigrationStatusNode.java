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

import static org.forgerock.openam.modernize.utils.NodeConstants.FALSE_OUTCOME_ID;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_PASSWORD_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_USERNAME_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.TRUE_OUTCOME_ID;
import static org.forgerock.openam.modernize.utils.NodeConstants.QUERY_IDM_QUERY_USER_PATH;

import java.io.IOException;
import java.util.List;
import java.util.ResourceBundle;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.modernize.utils.RequestUtils;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.sun.identity.sm.RequiredValueValidator;

public abstract class AbstractLegacyMigrationStatusNode implements Node {

	private Logger LOGGER = LoggerFactory.getLogger(AbstractLegacyMigrationStatusNode.class);

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
	 * Makes a call to the IDM user end point to check if the userName provided is
	 * migrated.
	 * 
	 * @param userName
	 * @return true if the user is found, false otherwise
	 * @throws NodeProcessException
	 */
	protected boolean getUserMigrationStatus(String userName, String idmEndpoint, String idmAdmin, String idmPassword)
			throws NodeProcessException {
		String getUserPathWithQuery = idmEndpoint + QUERY_IDM_QUERY_USER_PATH + "\'" + userName + "\'";
		LOGGER.debug("getUserMigrationStatus()::getUserPathWithQuery: " + getUserPathWithQuery);
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add(OPEN_IDM_ADMIN_USERNAME_HEADER, idmAdmin);
		headersMap.add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
		ResponseEntity<String> responseEntity = RequestUtils.sendGetRequest(getUserPathWithQuery,
				MediaType.APPLICATION_JSON, headersMap);
		return (getUserMigrationStatus(responseEntity));
	}

	/**
	 * 
	 * Validate in response if the user exists
	 * 
	 * @param responseEntity
	 * @return
	 * @throws NodeProcessException
	 */
	private boolean getUserMigrationStatus(ResponseEntity<String> responseEntity) throws NodeProcessException {
		LOGGER.debug("getUserMigrationStatus()::response.getBody(): " + responseEntity.getBody());
		ObjectMapper mapper = new ObjectMapper();
		JsonNode responseNode = null;
		try {
			responseNode = mapper.readTree(responseEntity.getBody());
		} catch (IOException e) {
			e.printStackTrace();
			throw new NodeProcessException("Unable to check if user is migrated: " + e.getMessage());
		}
		if (responseNode != null && responseNode.get("resultCount") != null
				&& responseNode.get("resultCount").asInt() > 0) {
			return true;
		}
		return false;
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
