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

	private Logger LOGGER = LoggerFactory.getLogger(AbstractLegacyMigrationStatusNode.class);

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
		String idmPasswordId();
	}

	/**
	 * Makes a call to the IDM user end point to check if the userName provided is
	 * migrated.
	 * 
	 * @param userName    the username that will be verified
	 * @param idmEndpoint the IDM host
	 * @param idmAdmin    the IDM administrator username
	 * @param idmPassword the IDM administrator password
	 * @return true if the user is found, false otherwise
	 * @throws NodeProcessException If there is an error
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 * @throws IOException
	 */
	public boolean getUserMigrationStatus(String userName, String idmEndpoint, String idmAdmin, String idmPassword,
			HttpClientHandler httpClientHandler)
			throws NodeProcessException, NeverThrowsException, InterruptedException, IOException {
		String getUserPathWithQuery = idmEndpoint + QUERY_IDM_QUERY_USER_PATH + "\'" + userName + "\'";
		LOGGER.debug("getUserMigrationStatus()::getUserPathWithQuery: " + getUserPathWithQuery);
		Response response = getUser(getUserPathWithQuery, idmAdmin, idmPassword, httpClientHandler);
		JsonValue jsonValues = JsonValue.json(response.getEntity().getJson());
		return (getUserMigrationStatus(jsonValues));
	}

	private Response getUser(String endpoint, String idmAdmin, String idmPassword, HttpClientHandler httpClientHandler)
			throws NeverThrowsException, InterruptedException {
		Request request = new Request();
		try {
			request.setMethod("GET").setUri(endpoint);
		} catch (URISyntaxException e) {
			LOGGER.error("getuser()::URISyntaxException: " + e);
		}
		request.getHeaders().add(OPEN_IDM_ADMIN_USERNAME_HEADER, idmAdmin);
		request.getHeaders().add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
		request.getHeaders().add("Content-Type", "application/json");
		Client client = new Client(httpClientHandler);
		return client.send(request).getOrThrow();
	}

	/**
	 * 
	 * Validate in the response if the user exists
	 * 
	 * @param jsonValues
	 * @return
	 * @throws NodeProcessException
	 */
	private boolean getUserMigrationStatus(JsonValue jsonValues) throws NodeProcessException {
		LOGGER.debug("getUserMigrationStatus()::response.getBody(): " + jsonValues);
		JsonValue value = JsonValue.json(jsonValues);
		if (value != null && value.get("resultCount") != null && value.get("resultCount").asInteger() > 0) {
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
