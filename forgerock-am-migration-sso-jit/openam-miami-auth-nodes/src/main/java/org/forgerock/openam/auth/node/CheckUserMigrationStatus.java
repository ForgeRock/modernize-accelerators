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

import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.miami.utils.NodeConstants.DEFAULT_IDM_USER;
import static org.forgerock.openam.miami.utils.NodeConstants.DEFAULT_IDM_USER_PASSWORD;
import static org.forgerock.openam.miami.utils.NodeConstants.OPEN_IDM_ADMIN_USERNAME_HEADER;
import static org.forgerock.openam.miami.utils.NodeConstants.OPEN_IDM_ADMIN_PASSWORD_HEADER;
import static org.forgerock.openam.miami.utils.NodeConstants.DEFAULT_IDM_USER_ENDPOINT;

import java.io.IOException;

import javax.inject.Inject;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.miami.utils.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

/**
 * 
 * <p>
 * This node validates if the user that accessed the tree is already migrated
 * into ForgeRock IDM.
 * </p>
 *
 */
@Node.Metadata(configClass = CheckUserMigrationStatus.Config.class, outcomeProvider = AbstractDecisionNode.OutcomeProvider.class)
public class CheckUserMigrationStatus extends AbstractDecisionNode {

	private Logger LOGGER = LoggerFactory.getLogger(CheckUserMigrationStatus.class);
	private final Config config;

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
		default String idmAdminPassword() {
			return DEFAULT_IDM_USER_PASSWORD;
		};

	}

	@Inject
	public CheckUserMigrationStatus(@Assisted CheckUserMigrationStatus.Config config) {
		this.config = config;
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String username = context.sharedState.get(USERNAME).asString();
		return goTo(getUserMigrationStatus(username)).build();
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
	private boolean getUserMigrationStatus(String userName) throws NodeProcessException {
		String getUserPathWithQuery = config.idmUserEndpoint() + "\"" + userName + "\"";
		LOGGER.debug("getUserMigrationStatus()::getUserPathWithQuery: " + getUserPathWithQuery);
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add(OPEN_IDM_ADMIN_USERNAME_HEADER, config.idmAdminUser());
		headersMap.add(OPEN_IDM_ADMIN_PASSWORD_HEADER, config.idmAdminPassword());
		ResponseEntity<String> responseEntity = RequestUtils.sendGetRequest(getUserPathWithQuery,
				MediaType.APPLICATION_JSON, headersMap);
		return (getUserMigrationStatus(responseEntity));
	}

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

}