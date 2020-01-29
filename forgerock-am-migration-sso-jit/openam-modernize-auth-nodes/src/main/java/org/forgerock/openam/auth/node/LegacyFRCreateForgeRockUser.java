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
import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_PASSWORD_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_USERNAME_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_EMAIL;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_FORCE_PASSWORD_RESET;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_GIVEN_NAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_NAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_SN;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

/**
 * 
 * <p>
 * This node validates creates a user in ForgeRock IDM.
 * </p>
 *
 */
@Node.Metadata(configClass = LegacyFRCreateForgeRockUser.Config.class, outcomeProvider = AbstractDecisionNode.OutcomeProvider.class)
public class LegacyFRCreateForgeRockUser extends AbstractDecisionNode {
	private static final String DEFAULT_LEGACY_IAM_ENV_URL = "legacyEnvURL";

	private Logger LOGGER = LoggerFactory.getLogger(LegacyFRCreateForgeRockUser.class);
	private final Config config;
	private String idmPassword;

	public interface Config {

		@Attribute(order = 1, validators = { RequiredValueValidator.class })
		default String legacyEnvURL() {
			return DEFAULT_LEGACY_IAM_ENV_URL;
		};

		@Attribute(order = 2, validators = { RequiredValueValidator.class })
		default String idmUserEndpoint() {
			return DEFAULT_IDM_USER_ENDPOINT;
		};

		@Attribute(order = 3, validators = { RequiredValueValidator.class })
		default String idmAdminUser() {
			return DEFAULT_IDM_USER;
		};

		@Attribute(order = 4, validators = { RequiredValueValidator.class })
		default String idmPasswordId() {
			return DEFAULT_IDM_USER_PASSWORD;
		};

		@Attribute(order = 5, validators = { RequiredValueValidator.class })
		default boolean setPasswordReset() {
			return false;
		};

	}

	@Inject
	public LegacyFRCreateForgeRockUser(@Assisted LegacyFRCreateForgeRockUser.Config config, @Assisted Realm realm,
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
		String legacyCookie = context.sharedState.get(LEGACY_COOKIE_SHARED_STATE_PARAM).asString();
		String userName = context.sharedState.get(USERNAME).asString();
		String password = "";
		if (context.transientState.get(PASSWORD) != null) {
			password = context.transientState.get(PASSWORD).asString();
		}
		if (legacyCookie != null) {
			MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
			headersMap.add("Cookie", legacyCookie);
			ResponseEntity<String> responseEntity = RequestUtils.sendGetRequest(config.legacyEnvURL() + userName,
					MediaType.APPLICATION_JSON, headersMap);

			// Read the user attributes from the respnose here. If any other attributes are
			// required, read here and use in createProvisioningRequestEntity()
			String firstName = null;
			String lastName = null;
			String email = null;
			if (responseEntity != null) {
				JsonNode response = null;
				try {
					ObjectMapper mapper = new ObjectMapper();
					response = mapper.readTree(responseEntity.getBody());
				} catch (IOException e) {
					LOGGER.error("process()::IOException: " + e.getMessage());
					e.printStackTrace();
				}
				if (response != null) {

					ArrayNode firstNameArrayNode = (ArrayNode) response.get(USER_GIVEN_NAME);
					if (firstNameArrayNode != null) {
						firstName = firstNameArrayNode.get(0).asText();
					}

					ArrayNode lastNameArrayNode = (ArrayNode) response.get(USER_SN);
					if (lastNameArrayNode != null) {
						lastName = lastNameArrayNode.get(0).asText();
					}

					ArrayNode mailArrayNode = (ArrayNode) response.get(USER_EMAIL);
					if (mailArrayNode != null) {
						email = mailArrayNode.get(0).asText();
					}
				}
			}
			return goTo(provisionUser(userName, password, firstName, lastName, email)).build();
		}
		return goTo(false).build();
	}

	/**
	 * 
	 * Create a user in ForgeRock DS via IDM user API
	 * 
	 * @param userName
	 * @param password
	 * @param firstName
	 * @param lastName
	 * @param email
	 * @return true if user was created successfully, false otherwise
	 */
	private boolean provisionUser(String userName, String password, String firstName, String lastName, String email) {
		LOGGER.error("provisionUser()::Start");
		String jsonBody = createProvisioningRequestEntity(userName, password, firstName, lastName, email);
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add(OPEN_IDM_ADMIN_USERNAME_HEADER, config.idmAdminUser());
		headersMap.add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
		ResponseEntity<String> responseStatusCode = RequestUtils.sendPostRequest(config.idmUserEndpoint(), jsonBody,
				MediaType.APPLICATION_JSON, headersMap);
		if (responseStatusCode != null) {
			if (responseStatusCode.getStatusCodeValue() == 201) {
				LOGGER.error("provisionUser()::End - success - 201 created");
				return true;
			}
		}
		LOGGER.error("provisionUser()::End - fail");
		return false;
	}

	/**
	 * 
	 * Creates the request body in the format required by the IDM API in order to
	 * create a user in DS.
	 * 
	 * @param userName
	 * @param password
	 * @param firstName
	 * @param lastName
	 * @param email
	 * @return JSON string
	 */
	private String createProvisioningRequestEntity(String userName, String password, String firstName, String lastName,
			String email) {
		LOGGER.error("createProvisioningRequestEntity()::userName: " + userName);
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode node = mapper.createObjectNode();
		node.put(USER_GIVEN_NAME, firstName);
		node.put(USER_SN, lastName);
		node.put(USER_EMAIL, email);
		node.put(USER_NAME, userName);
		// For the case user is migrated without password
		if (password != null && password.length() > 0) {
			node.put(PASSWORD, password);
		}
		if (config.setPasswordReset()) {
			node.put(USER_FORCE_PASSWORD_RESET, true);
		}
		String jsonString = null;
		try {
			jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
		} catch (JsonProcessingException e) {
			LOGGER.error("createProvisioningRequestEntity()::Error creating provisioning entity: " + e.getMessage());
			e.printStackTrace();
		}
		LOGGER.debug("createProvisioningRequestEntity()::entity: " + jsonString);
		return jsonString;
	}

}