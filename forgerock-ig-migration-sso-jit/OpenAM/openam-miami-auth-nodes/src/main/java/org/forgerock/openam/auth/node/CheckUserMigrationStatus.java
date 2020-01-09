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

@Node.Metadata(configClass = CheckUserMigrationStatus.Config.class, outcomeProvider = AbstractDecisionNode.OutcomeProvider.class)
public class CheckUserMigrationStatus extends AbstractDecisionNode {

	private Logger LOGGER = LoggerFactory.getLogger(CheckUserMigrationStatus.class);
	private final Config config;

	public interface Config {

		@Attribute(order = 1, validators = { RequiredValueValidator.class })
		default String idmUserEndpoint() {
			return "https://idm.dev.miami-accelerators.com/openidm/managed/user?_queryFilter=userName+eq+";
		};

		@Attribute(order = 2, validators = { RequiredValueValidator.class })
		default String idmAdminUser() {
			return "openidm-admin";
		};

		@Attribute(order = 3, validators = { RequiredValueValidator.class })
		default String idmAdminPassword() {
			return "openidm-admin";
		};

	}

	@Inject
	public CheckUserMigrationStatus(@Assisted CheckUserMigrationStatus.Config config) {
		this.config = config;
	}

	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String username = context.sharedState.get(USERNAME).asString();
		return goTo(getUserMigrationStatus(username)).build();
	}

	private boolean getUserMigrationStatus(String userName) throws NodeProcessException {
		String getUserPathWithQuery = config.idmUserEndpoint() + "\"" + userName + "\"";
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add("X-OpenIDM-Username", config.idmAdminUser());
		headersMap.add("X-OpenIDM-Password", config.idmAdminPassword());
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