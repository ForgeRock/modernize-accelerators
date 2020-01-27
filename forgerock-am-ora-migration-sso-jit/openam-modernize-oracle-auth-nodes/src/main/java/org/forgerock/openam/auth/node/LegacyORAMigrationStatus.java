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
import static org.forgerock.openam.modernize.utils.NodeConstants.DEFAULT_IDM_USER;
import static org.forgerock.openam.modernize.utils.NodeConstants.DEFAULT_IDM_USER_ENDPOINT;
import static org.forgerock.openam.modernize.utils.NodeConstants.DEFAULT_IDM_USER_PASSWORD;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_PASSWORD_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_USERNAME_HEADER;

import java.io.IOException;

import javax.inject.Inject;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.modernize.utils.RequestUtils;
import org.forgerock.openam.secrets.Secrets;
import org.forgerock.openam.secrets.SecretsProviderFacade;
import org.forgerock.secrets.NoSuchSecretException;
import org.forgerock.secrets.Purpose;
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
@Node.Metadata(configClass = LegacyORAMigrationStatus.Config.class, outcomeProvider = AbstractDecisionNode.OutcomeProvider.class)
public class LegacyORAMigrationStatus extends AbstractDecisionNode {

	private Logger LOGGER = LoggerFactory.getLogger(LegacyORAMigrationStatus.class);
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
	public LegacyORAMigrationStatus(@Assisted LegacyORAMigrationStatus.Config config, @Assisted Realm realm,
			Secrets secrets) throws NodeProcessException {
		this.config = config;
		SecretsProviderFacade secretsProvider = secrets.getRealmSecrets(realm);
		try {
			this.idmPassword = secretsProvider.getNamedSecret(Purpose.PASSWORD, config.idmPasswordId())
					.getOrThrowUninterruptibly().revealAsUtf8(String::valueOf);
		} catch (NoSuchSecretException e) {
			throw new NodeProcessException("No secret " + config.idmPasswordId() + " found");
		}
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
		headersMap.add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
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