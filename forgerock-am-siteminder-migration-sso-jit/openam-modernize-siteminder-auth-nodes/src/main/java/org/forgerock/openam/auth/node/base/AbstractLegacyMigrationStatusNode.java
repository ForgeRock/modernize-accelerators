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

import static org.forgerock.openam.modernize.utils.NodeConstants.FALSE_OUTCOME_ID;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_PASSWORD_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_USERNAME_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.QUERY_IDM_QUERY_USER_PATH;
import static org.forgerock.openam.modernize.utils.NodeConstants.TRUE_OUTCOME_ID;

import java.io.IOException;
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
import org.forgerock.util.i18n.PreferredLocales;
import org.forgerock.util.promise.NeverThrowsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.sun.identity.sm.RequiredValueValidator;

/**
 * This class serves as a base for the LegacyMigrationStatus node.
 */
public abstract class AbstractLegacyMigrationStatusNode implements Node {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLegacyMigrationStatusNode.class);

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
		 * Defines the value for the IDM administrator user name that is used when
		 * creating a new user.
		 * 
		 * @return the configured value for the IDM administrator user name that is used
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
		String idmPasswordId();
	}

	/**
	 * Makes a call to the IDM user end point to check if the userName provided is
	 * migrated.
	 * 
	 * @param userName    the user name that will be verified
	 * @param idmEndpoint the IDM host
	 * @param idmAdmin    the IDM administrator user name
	 * @param idmPassword the IDM administrator password
	 * @return true if the user is found, false otherwise
	 * @throws NodeProcessException If there is an error
	 * @throws IOException
	 */
	public boolean getUserMigrationStatus(String userName, String idmEndpoint, String idmAdmin, String idmPassword,
			HttpClientHandler httpClientHandler) throws NodeProcessException, IOException {
		String getUserPathWithQuery = idmEndpoint + QUERY_IDM_QUERY_USER_PATH + "\'" + userName + "\'";
		LOGGER.debug("AbstractLegacyMigrationStatusNode::getUserMigrationStatus() > getUserPathWithQuery: {}",
				getUserPathWithQuery);
		Response response = getUser(getUserPathWithQuery, idmAdmin, idmPassword, httpClientHandler);
		if (response != null) {
			JsonValue jsonValues = JsonValue.json(response.getEntity().getJson());
			LOGGER.debug("AbstractLegacyMigrationStatusNode::getUserMigrationStatus() > jsonValues: {}", jsonValues);
			return getUserMigrationStatus(jsonValues);
		}
		return false;
	}

	/**
	 * 
	 * Checks in ForgeRock IDM if the given user in the endpoint parameter exists.
	 * 
	 * @param endpoint          the ForgeRock IDM user endpoint
	 * @param idmAdmin          the IDM administrator user name
	 * @param idmPassword       the IDM administrator password
	 * @param httpClientHandler the ForgeRock {@link HttpClientHandler}
	 * @return the response received from ForgeRock IDM
	 */
	private Response getUser(String endpoint, String idmAdmin, String idmPassword,
			HttpClientHandler httpClientHandler) {
		Request request = new Request();
		try {
			request.setMethod("GET").setUri(endpoint);
		} catch (URISyntaxException e) {
			LOGGER.error("AbstractLegacyMigrationStatusNode::getuser() > URISyntaxException: {}", e);
		}
		request.getHeaders().add(OPEN_IDM_ADMIN_USERNAME_HEADER, idmAdmin);
		request.getHeaders().add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
		request.getHeaders().add("Content-Type", "application/json");
		Client client = new Client(httpClientHandler);
		try {
			return client.send(request).getOrThrow();
		} catch (NeverThrowsException e) {
			LOGGER.error("AbstractLegacyMigrationStatusNode::getUserMigrationStatus() > NeverThrowsException: {}", e);
		} catch (InterruptedException e) {
			LOGGER.error("AbstractLegacyMigrationStatusNode::getUserMigrationStatus() > InterruptedException: {}", e);
			Thread.currentThread().interrupt();
		}
		return null;
	}

	/**
	 * 
	 * Validate in the response if the user exists
	 * 
	 * @param jsonValues
	 * @return true if the user count is greater that zero, false otherwise.
	 */
	public boolean getUserMigrationStatus(JsonValue jsonValues) {
		LOGGER.debug("AbstractLegacyMigrationStatusNode::getUserMigrationStatus() > response.getBody(): {}",
				jsonValues);
		if (jsonValues != null && jsonValues.isDefined("resultCount")) {
			return jsonValues.get("resultCount").asInteger() > 0;
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
