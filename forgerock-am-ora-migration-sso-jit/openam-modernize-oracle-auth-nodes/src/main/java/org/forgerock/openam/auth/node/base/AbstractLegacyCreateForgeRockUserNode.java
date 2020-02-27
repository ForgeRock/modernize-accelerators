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

import java.net.URISyntaxException;
import java.util.List;
import java.util.ResourceBundle;

import org.forgerock.http.Client;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.template.ForgeRockUserAttributesTemplate;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.secrets.Secrets;
import org.forgerock.openam.secrets.SecretsProviderFacade;
import org.forgerock.secrets.NoSuchSecretException;
import org.forgerock.secrets.Purpose;
import org.forgerock.util.i18n.PreferredLocales;
import org.forgerock.util.promise.NeverThrowsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

/**
 * This class serves as a base for the CreateForgeRockUser node.
 */
public abstract class AbstractLegacyCreateForgeRockUserNode implements Node {

	private Logger LOGGER = LoggerFactory.getLogger(AbstractLegacyCreateForgeRockUserNode.class);
	protected final AbstractLegacyCreateForgeRockUserNode.Config config;
	protected String idmPassword;
	protected final HttpClientHandler httpClientHandler;

	/**
	 * The configuration for this node.
	 */
	public interface Config {
		/**
		 * Defines the URL used for retrieving the profile information from the legacy
		 * IAM
		 * 
		 * @return the configured URL used for retrieving the profile information from
		 *         the legacy IAM
		 */
		@Attribute(order = 1, validators = { RequiredValueValidator.class })
		String legacyEnvURL();

		/**
		 * Defines the ForgeRock IDM URL
		 * 
		 * @return the configured ForgeRock IDM URL
		 */
		@Attribute(order = 2, validators = { RequiredValueValidator.class })
		String idmUserEndpoint();

		/**
		 * Defines the value for the IDM administrator username that is used when
		 * creating a new user.
		 * 
		 * @return the configured value for the IDM administrator username that is used
		 *         when creating a new user
		 */
		@Attribute(order = 3, validators = { RequiredValueValidator.class })
		String idmAdminUser();

		/**
		 * Defines the secret id that is used to retrieve the IDM administrator user's
		 * password
		 * 
		 * @return the configured secret id that is used to retrieve the IDM
		 *         administrator useruser's password
		 */
		@Attribute(order = 4, validators = { RequiredValueValidator.class })
		String idmPasswordId();

		/**
		 * Specifies if the set password reset attribute must be set for the current
		 * user.
		 * 
		 * @return true to set the password reset flag on the user, false otherwise.
		 */
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
			@Assisted Realm realm, Secrets secrets, HttpClientHandler httpClientHandler) throws NodeProcessException {
		this.config = config;
		this.httpClientHandler = httpClientHandler;
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
	 * Create a user in ForgeRock DS via IDM user API
	 * 
	 * @param userAttributes object holding the user's attributes
	 * @return <b>true</b> if creation was successful, <b>false</b> otherwise
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 */
	protected boolean provisionUser(ForgeRockUserAttributesTemplate userAttributes)
			throws NeverThrowsException, InterruptedException {
		LOGGER.debug("provisionUser()::Start");
		JsonValue jsonBody = createProvisioningRequestEntity(userAttributes);
		Response reaponse = createUser(config.idmUserEndpoint() + QUERY_IDM_CREATE_USER_PATH, config.idmAdminUser(),
				idmPassword, jsonBody);
		if (reaponse.getStatus().isSuccessful()) {
			LOGGER.debug("provisionUser()::End - success - 201 created");
			return true;
		}
		LOGGER.debug("provisionUser()::End - failure scenario");
		return false;
	}

	private Response createUser(String endpoint, String idmAdmin, String idmPassword, JsonValue jsonBody)
			throws NeverThrowsException, InterruptedException {
		Request request = new Request();
		try {
			request.setMethod("POST").setUri(endpoint);
		} catch (URISyntaxException e) {
			LOGGER.error("getuser()::URISyntaxException: " + e);
		}
		request.getHeaders().add(OPEN_IDM_ADMIN_USERNAME_HEADER, idmAdmin);
		request.getHeaders().add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
		request.getHeaders().add("Content-Type", "application/json");
		request.setEntity(jsonBody);
		Client client = new Client(httpClientHandler);
		return client.send(request).getOrThrow();
	}

	/**
	 * Creates the request body in the format required by the IDM API in order to
	 * create a user in DS.
	 * 
	 * @param userAttributes object holding the user's attributes
	 * @return JSON string in the format required by the IDM API in order to create
	 *         a user in DS.
	 */
	private JsonValue createProvisioningRequestEntity(ForgeRockUserAttributesTemplate userAttributes) {
		LOGGER.error("createProvisioningRequestEntity()::userName: " + userAttributes.getUserName());

		JsonValue value = JsonValue
				.json(JsonValue.object(JsonValue.field(USER_GIVEN_NAME, userAttributes.getFirstName()),
						JsonValue.field(USER_SN, userAttributes.getLastName()),
						JsonValue.field(USER_EMAIL, userAttributes.getEmail()),
						JsonValue.field(USER_NAME, userAttributes.getUserName())));

		// For the case user is migrated without password
		if (userAttributes.getPassword() != null && userAttributes.getPassword().length() > 0) {
			value.add(PASSWORD, userAttributes.getPassword());
		}
		if (config.setPasswordReset()) {
			value.add(USER_FORCE_PASSWORD_RESET, true);
		}
		return value;
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
