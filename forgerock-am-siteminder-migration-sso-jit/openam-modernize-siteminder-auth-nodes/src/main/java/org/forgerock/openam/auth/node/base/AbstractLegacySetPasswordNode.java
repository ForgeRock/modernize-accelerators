/***************************************************************************
 *  Copyright 2020 ForgeRock AS
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
import org.forgerock.util.annotations.VisibleForTesting;
import org.forgerock.util.i18n.PreferredLocales;
import org.forgerock.util.promise.NeverThrowsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.sun.identity.sm.RequiredValueValidator;

/**
 * This class serves as a base for the LegacySetPassword node.
 */
public abstract class AbstractLegacySetPasswordNode implements Node {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLegacySetPasswordNode.class);

	/**
	 * The configuration for this node.
	 */
	public interface Config {

		/**
		 * Defines the ForgeRock IDM URL
		 * 
		 * @return the configured ForgeRock IDM URL
		 */
		@Attribute(order = 1, validators = { RequiredValueValidator.class })
		String idmUserEndpoint();

		/**
		 * Defines the value for the IDM administrator username that is used when
		 * creating a new user.
		 * 
		 * @return the configured value for the IDM administrator username that is used
		 *         when creating a new user
		 */
		@Attribute(order = 2, validators = { RequiredValueValidator.class })
		String idmAdminUser();

		/**
		 * Defines the secret id that is used to retrieve the IDM administrator user's
		 * password
		 * 
		 * @return the configured secret id that is used to retrieve the IDM
		 *         administrator useruser's password
		 */
		@Attribute(order = 3, validators = { RequiredValueValidator.class })
		String idmPassworSecretdId();
	}

	/**
	 * Sets the user password via the IDM API
	 * 
	 * @param userName          the user name of the user being authenticated
	 * @param password          the password of the user being authenticated
	 * @param idmEndpoint       the IDM endpoint
	 * @param idmAdmin          the IDM administrator user name
	 * @param idmPassword       the IDM administrator user password
	 * @param httpClientHandler
	 * @return <b>true</b> if the update operation is successful, <b>false</b>
	 *         otherwise
	 */
	@VisibleForTesting
	public boolean setUserPassword(String userName, String password, String idmEndpoint, String idmAdmin,
			String idmPassword, HttpClientHandler httpClientHandler) {
		LOGGER.error("AbstractLegacySetPasswordNode::setUserPassword() > Start");
		JsonValue jsonBody = createPasswordRequestEntity(password);
		Response response = callUpdatePassword(idmEndpoint + PATCH_IDM_USER_PATH + "'" + userName + "'", idmAdmin,
				idmPassword, jsonBody, httpClientHandler);
		if (response != null && response.getStatus().isSuccessful()) {
			LOGGER.error("AbstractLegacySetPasswordNode::setUserPassword() > End with status {}", response.getStatus());
			return true;
		}
		LOGGER.error("AbstractLegacySetPasswordNode::setUserPassword() > End - failure");
		return false;
	}

	/**
	 * Creates the request body for password update, in the format accepted by IDM
	 * 
	 * @param password
	 * @return JSON string
	 */
	public JsonValue createPasswordRequestEntity(String password) {
		LOGGER.debug("createPasswordRequestEntity()::Start");
		return JsonValue.json(JsonValue.array(
				JsonValue.object(JsonValue.field("operation", "replace"), JsonValue.field("field", PASSWORD),
						JsonValue.field("value", password)),
				JsonValue.object(JsonValue.field("operation", "replace"),
						JsonValue.field("field", USER_FORCE_PASSWORD_RESET), JsonValue.field("value", false))));
	}

	private Response callUpdatePassword(String endpoint, String idmAdmin, String idmPassword, JsonValue jsonBody,
			HttpClientHandler httpClientHandler) {
		Request request = new Request();
		try {
			request.setMethod("POST").setUri(endpoint);
		} catch (URISyntaxException e) {
			LOGGER.error("AbstractLegacySetPasswordNode::createPasswordRequestEntity() > URISyntaxException: {}", e);
		}
		request.getHeaders().add(OPEN_IDM_ADMIN_USERNAME_HEADER, idmAdmin);
		request.getHeaders().add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
		request.getHeaders().add("Content-Type", "application/json");
		request.setEntity(jsonBody);
		Client client = new Client(httpClientHandler);
		try {
			return client.send(request).getOrThrow();
		} catch (NeverThrowsException e) {
			LOGGER.error("AbstractLegacySetPasswordNode::createPasswordRequestEntity() > NeverThrowsException: {}", e);
		} catch (InterruptedException e) {
			LOGGER.error("AbstractLegacySetPasswordNode::createPasswordRequestEntity() > InterruptedException: {}", e);
			Thread.currentThread().interrupt();
		}
		return null;
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
