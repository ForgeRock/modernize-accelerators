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
import static org.forgerock.openam.modernize.utils.NodeConstants.ORA_FIRST_NAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.ORA_LAST_NAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.ORA_MAIL;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.inject.Inject;

import org.forgerock.http.Client;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractLegacyCreateForgeRockUserNode;
import org.forgerock.openam.auth.node.template.ForgeRockUserAttributesTemplate;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.secrets.Secrets;
import org.forgerock.openam.secrets.SecretsProviderFacade;
import org.forgerock.secrets.NoSuchSecretException;
import org.forgerock.secrets.Purpose;
import org.forgerock.util.promise.NeverThrowsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;

/**
 * <p>
 * A node which creates a user in ForgeRock IDM by calling the user endpoint
 * with the action query parameter set to create:
 * <b><i>{@code ?_action=create}</i></b>.
 * </p>
 */
@Node.Metadata(configClass = AbstractLegacyCreateForgeRockUserNode.Config.class, outcomeProvider = AbstractLegacyCreateForgeRockUserNode.OutcomeProvider.class)
public class LegacyORACreateForgeRockUser extends AbstractLegacyCreateForgeRockUserNode {

	private Logger LOGGER = LoggerFactory.getLogger(LegacyORACreateForgeRockUser.class);
	private final AbstractLegacyCreateForgeRockUserNode.Config config;
	private String idmPassword;
	private final HttpClientHandler httpClientHandler;

	/**
	 * Creates a LegacyORACreateForgeRockUser node with the provided configuration
	 * 
	 * @param config  the configuration for this Node.
	 * @param realm   the realm the node is accessed from.
	 * @param secrets the secret store used to get passwords
	 * @throws NodeProcessException If there is an error reading the configuration.
	 */
	@Inject
	public LegacyORACreateForgeRockUser(@Assisted LegacyORACreateForgeRockUser.Config config, @Assisted Realm realm,
			Secrets secrets, HttpClientHandler httpClientHandler) throws NodeProcessException {
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
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		LOGGER.error("Start process");
		String legacyCookie = context.sharedState.get(LEGACY_COOKIE_SHARED_STATE_PARAM).asString();
		String userName = context.sharedState.get(USERNAME).asString();
		String password = "";
		// Password is null in this point if we're on the flow that migrates the user
		// that is previously logged in the legacy IAM, on the same domain
		if (context.transientState.get(PASSWORD) != null) {
			password = context.transientState.get(PASSWORD).asString();
		}
		if (legacyCookie != null) {
			ForgeRockUserAttributesTemplate userAttributes;
			try {
				userAttributes = getUserAttributes(userName, password, legacyCookie);
				if (userAttributes != null) {
					return goTo(provisionUser(userAttributes, idmPassword, config.idmUserEndpoint(),
							config.idmAdminUser(), config.setPasswordReset(), httpClientHandler)).build();
				}
			} catch (NeverThrowsException e) {
				throw new NodeProcessException("NeverThrowsException in async call: " + e);
			} catch (InterruptedException e) {
				throw new NodeProcessException("InterruptedException: " + e);
			} catch (IOException e1) {
				throw new NodeProcessException("IOException: " + e1);
			}

		}
		return goTo(false).build();
	}

	/**
	 * Requests the user details endpoint and reads the user attributes, which then
	 * adds on {@link ForgeRockUserAttributesTemplate}
	 * 
	 * @param userName     - the userName for the user that we need to get details
	 *                     from the legacy IAM. Also used when creating the user
	 *                     into IDM
	 * @param              password- the password for the user that we need to get
	 *                     details from the legacy IAM. Also used when creating the
	 *                     user into IDM
	 * @param legacyCookie - the SSO token used to get access to the user
	 *                     information from the legacy IAM. Must be valid otherwise
	 *                     the method fails and returns null
	 * @return <b>null</b> if there is an error, otherwise
	 *         {@link ForgeRockUserAttributesTemplate} if legacy IAM was called
	 *         successfully and the user information was retrieved.
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 * @throws IOException
	 */
	private ForgeRockUserAttributesTemplate getUserAttributes(String userName, String password, String legacyCookie)
			throws NeverThrowsException, InterruptedException, IOException {
		LOGGER.debug("getUserAttributes()::Start");
		// Call OAM user details API
		Response response = getUser(config.legacyEnvURL() + userName, legacyCookie);
		JsonValue entity = JsonValue.json(response.getEntity().getJson());
		// Read the user attributes from the response here. If any other attributes are
		// required, read here and extend the ForgeRockUserAttributesTemplate
		String firstName = null;
		String lastName = null;
		String email = null;

		if (entity != null) {
			if (entity.get(ORA_FIRST_NAME) != null)
				firstName = entity.get(ORA_FIRST_NAME).asString();
			if (entity.get(ORA_LAST_NAME) != null)
				lastName = entity.get(ORA_LAST_NAME).asString();
			if (entity.get(ORA_MAIL) != null)
				email = entity.get(ORA_MAIL).asString();
		}

		if (userName != null && firstName != null && lastName != null && email != null) {
			ForgeRockUserAttributesTemplate userAttributes = new ForgeRockUserAttributesTemplate();
			userAttributes.setUserName(userName);
			userAttributes.setPassword(password);
			userAttributes.setFirstName(firstName);
			userAttributes.setLastName(lastName);
			userAttributes.setEmail(email);
			return userAttributes;
		}
		return null;
	}

	private Response getUser(String endpoint, String legacyCookie) throws NeverThrowsException, InterruptedException {
		Request request = new Request();
		try {
			request.setMethod("GET").setUri(endpoint);
		} catch (URISyntaxException e) {
			LOGGER.error("getuser()::URISyntaxException: " + e);
		}
		request.getHeaders().add("Cookie", legacyCookie);
		request.getHeaders().add("Content-Type", "application/json");
		Client client = new Client(httpClientHandler);
		return client.send(request).getOrThrow();
	}

}