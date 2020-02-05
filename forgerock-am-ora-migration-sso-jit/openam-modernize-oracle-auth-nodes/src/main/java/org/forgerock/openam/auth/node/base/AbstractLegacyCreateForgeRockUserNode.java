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
import static org.forgerock.openam.modernize.utils.NodeConstants.QUERY_IDM_CREATE_USER_PATH;
import static org.forgerock.openam.modernize.utils.NodeConstants.TRUE_OUTCOME_ID;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_EMAIL;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_FORCE_PASSWORD_RESET;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_GIVEN_NAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_NAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_SN;

import java.util.List;
import java.util.ResourceBundle;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.template.ForgeRockUserAttributesTemplate;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.modernize.utils.RequestUtils;
import org.forgerock.openam.secrets.Secrets;
import org.forgerock.openam.secrets.SecretsProviderFacade;
import org.forgerock.secrets.NoSuchSecretException;
import org.forgerock.secrets.Purpose;
import org.forgerock.util.i18n.PreferredLocales;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

public abstract class AbstractLegacyCreateForgeRockUserNode implements Node {

	private Logger LOGGER = LoggerFactory.getLogger(AbstractLegacyCreateForgeRockUserNode.class);
	protected final AbstractLegacyCreateForgeRockUserNode.Config config;
	protected String idmPassword;

	/**
	 * The Config.
	 */
	public interface Config {
		@Attribute(order = 1, validators = { RequiredValueValidator.class })
		String legacyEnvURL();

		@Attribute(order = 2, validators = { RequiredValueValidator.class })
		String idmUserEndpoint();

		@Attribute(order = 3, validators = { RequiredValueValidator.class })
		String idmAdminUser();

		@Attribute(order = 4, validators = { RequiredValueValidator.class })
		String idmPasswordId();

		@Attribute(order = 5, validators = { RequiredValueValidator.class })
		default boolean setPasswordReset() {
			return false;
		};
	}

	/**
	 * Constructs a new {@link AbstractLegacyCreateForgeRockUserNode} with the
	 * provided {@link AbstractLegacyCreateForgeRockUserNode.Config}.
	 *
	 * @param config provides the settings for initializing an
	 *               {@link AbstractLegacyCreateForgeRockUserNode}.
	 * @throws NodeProcessException
	 */
	public AbstractLegacyCreateForgeRockUserNode(AbstractLegacyCreateForgeRockUserNode.Config config,
			@Assisted Realm realm, Secrets secrets) throws NodeProcessException {
		this.config = config;
		SecretsProviderFacade secretsProvider = secrets.getRealmSecrets(realm);
		if (secretsProvider != null) {
			try {
				this.idmPassword = secretsProvider.getNamedSecret(Purpose.PASSWORD, config.idmPasswordId())
						.getOrThrowUninterruptibly().revealAsUtf8(String::valueOf);
			} catch (NoSuchSecretException e) {
				throw new NodeProcessException("No secret " + config.idmPasswordId() + " found");
			}
		}
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
	protected boolean provisionUser(ForgeRockUserAttributesTemplate userAttributes) {
		LOGGER.debug("provisionUser()::Start");
		String jsonBody = createProvisioningRequestEntity(userAttributes);
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add(OPEN_IDM_ADMIN_USERNAME_HEADER, config.idmAdminUser());
		headersMap.add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
		ResponseEntity<String> responseStatusCode = RequestUtils.sendPostRequest(
				config.idmUserEndpoint() + QUERY_IDM_CREATE_USER_PATH, jsonBody, MediaType.APPLICATION_JSON,
				headersMap);
		if (responseStatusCode != null) {
			if (responseStatusCode.getStatusCodeValue() == 201) {
				LOGGER.debug("provisionUser()::End - success - 201 created");
				return true;
			}
		}
		LOGGER.debug("provisionUser()::End - failure scenario");
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
	private String createProvisioningRequestEntity(ForgeRockUserAttributesTemplate userAttributes) {
		LOGGER.error("createProvisioningRequestEntity()::userName: " + userAttributes.getUserName());
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode node = mapper.createObjectNode();
		node.put(USER_GIVEN_NAME, userAttributes.getFirstName());
		node.put(USER_SN, userAttributes.getLastName());
		node.put(USER_EMAIL, userAttributes.getEmail());
		node.put(USER_NAME, userAttributes.getUserName());
		// For the case user is migrated without password
		if (userAttributes.getPassword() != null && userAttributes.getPassword().length() > 0) {
			node.put(PASSWORD, userAttributes.getPassword());
		}
		if (config.setPasswordReset()) {
			node.put(USER_FORCE_PASSWORD_RESET, true);
		}
		String jsonString = null;
		try {
			jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
		} catch (JsonProcessingException e) {
			LOGGER.error("createProvisioningRequestEntity()::Error creating user entity: " + e.getMessage());
			e.printStackTrace();
		}
		LOGGER.debug("createProvisioningRequestEntity()::entity: " + jsonString);
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
