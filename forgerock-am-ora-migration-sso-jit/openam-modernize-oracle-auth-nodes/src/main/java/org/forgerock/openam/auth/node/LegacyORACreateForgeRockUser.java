/***************************************************************************
 *  Copyright 2021 ForgeRock AS
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

import static org.forgerock.openam.auth.node.api.SharedStateConstants.OBJECT_ATTRIBUTES;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Headers.APPLICATION_JSON;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Headers.CONTENT_TYPE;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Headers.COOKIE;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Methods.GET;
import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;

import javax.inject.Inject;

import org.forgerock.http.Client;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractLegacyCreateForgeRockUserNode;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.modernize.utils.LegacyORAObjectAttributesHandler;
import org.forgerock.openam.services.OracleService;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.RequiredValueValidator;
import com.sun.identity.sm.SMSException;

/**
 * <p>
 * A node which creates a user in ForgeRock IDM by calling the user endpoint
 * with the action query parameter set to create:
 * <b><i>{@code ?_action=create}</i></b>.
 * </p>
 */
@Node.Metadata(configClass = LegacyORACreateForgeRockUser.LegacyORAConfig.class, outcomeProvider = AbstractLegacyCreateForgeRockUserNode.OutcomeProvider.class)
public class LegacyORACreateForgeRockUser extends AbstractLegacyCreateForgeRockUserNode {

	private final Logger logger = LoggerFactory.getLogger(LegacyORACreateForgeRockUser.class);
	private final LegacyORAConfig config;
	OracleService oracleService;
	LegacyORAObjectAttributesHandler legacyORAObjectAttributesHandler;

	public interface LegacyORAConfig extends AbstractLegacyCreateForgeRockUserNode.Config {
		/**
		 * A map which should hold as keys the name of the SiteMinder user attributes,
		 * and as values their equivalent name in the ForgeRock IDM database.
		 *
		 * @return the configured attributes map
		 */
		@Attribute(order = 108, validators = { RequiredValueValidator.class })
		Map<String, String> migrationAttributesMap();
	}

	/**
	 * Creates a LegacyORACreateForgeRockUser node with the provided configuration
	 *
	 * @param config          the configuration for this Node.
	 * @param realm           the realm of the current Node.
	 * @param serviceRegistry instance of the tree's serice config.
	 */
	@Inject
	public LegacyORACreateForgeRockUser(@Assisted Realm realm, @Assisted LegacyORAConfig config,
			AnnotatedServiceRegistry serviceRegistry) {
		this.config = config;
		this.legacyORAObjectAttributesHandler = LegacyORAObjectAttributesHandler.getInstance();

		try {
			oracleService = serviceRegistry.getRealmSingleton(OracleService.class, realm).get();
		} catch (SSOException | SMSException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Main method called when the node is triggered.
	 *
	 * @param context the tree context
	 */
	@Override
	public Action process(TreeContext context) {
		logger.info("LegacyORACreateForgeRockUser::process > Starting process");
		String legacyCookie = context.sharedState.get(LEGACY_COOKIE_SHARED_STATE_PARAM).asString();
		String userName = context.sharedState.get(USERNAME).asString();

		if (legacyCookie != null) {
			Response response;
			JsonValue entity;

			try {
				response = getUser(oracleService.legacyEnvURL() + userName, legacyCookie);
				if (!response.getStatus().isSuccessful()) {
					return goTo(false).build();
				}
				entity = JsonValue.json(response.getEntity().getJson());
				return updateStates(context, entity);
			} catch (IOException e) {
				logger.error("LegacyORACreateForgeRockUser::process > IOException {0}", e);
			} catch (InterruptedException e) {
				logger.error("LegacyORACreateForgeRockUser::process > InterruptedException {0}", e);
				Thread.currentThread().interrupt();
			}
		}
		return goTo(false).build();
	}

	/**
	 * Updates both the sharedState and the transientState (if it's the case)
	 * SharedState will receive an OBJECT_ATTRIBUTES object which will contain all
	 * the relevant info for the specified user TransientState will receive a
	 * password set for the specified user
	 *
	 * @param context the tree context
	 * @param entity  the response body
	 * @return the action
	 */
	public Action updateStates(TreeContext context, JsonValue entity) {
		JsonValue copySharedState = context.sharedState.copy();
		Action.ActionBuilder resultedAction = goTo(true);

		// Dispatches to the overridden 'updateObjectAttributes' method in the extending
		// utils class
		JsonValue userAttributes = legacyORAObjectAttributesHandler.updateObjectAttributes(entity, copySharedState,
				config.migrationAttributesMap());
		if (config.setPasswordReset()) {
			JsonValue userAttributesTransientState = legacyORAObjectAttributesHandler.setPassword(context);

			if (userAttributesTransientState == null) {
				return goTo(true).build();
			}

			resultedAction
					.replaceTransientState(context.transientState.put(OBJECT_ATTRIBUTES, userAttributesTransientState));
		}

		if (userAttributes != null) {
			resultedAction.replaceSharedState(context.sharedState.put(OBJECT_ATTRIBUTES, userAttributes));
		}

		return resultedAction.build();
	}

	/**
	 * Makes a GET request to the specified endpoint in order to retrieve the user's
	 * data
	 *
	 * @param endpoint     the endpoint to which we need to make the call
	 * @param legacyCookie the cookie of the legacy instance
	 * @return the response
	 * @throws InterruptedException when an exception occurs
	 */
	public Response getUser(String endpoint, String legacyCookie) throws InterruptedException {
		try (Request request = new Request()) {
			request.setMethod(GET).setUri(endpoint);
			request.getHeaders().add(COOKIE, legacyCookie);
			request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);

			try (HttpClientHandler httpClientHandler = new HttpClientHandler()) {
				Client client = new Client(httpClientHandler);
				return client.send(request).getOrThrow();
			}
		} catch (URISyntaxException | HttpApplicationException | IOException e) {
			logger.error("LegacyORACreateForgeRockUser::getUser > Failed. Exception: {0}", e);
		}

		return null;
	}
}