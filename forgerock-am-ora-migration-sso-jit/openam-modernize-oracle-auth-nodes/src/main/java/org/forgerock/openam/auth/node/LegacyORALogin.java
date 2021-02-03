/***************************************************************************
 *  Copyright 2019-2021 ForgeRock AS
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
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE;
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE_DOMAIN;
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE_NAME;

import java.util.UUID;

import javax.inject.Inject;

import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractLegacyLoginNode;
import org.forgerock.openam.auth.node.treehook.LegacyORASessionTreeHook;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.modernize.legacy.ORAAccessClient;
import org.forgerock.openam.services.OracleService;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

import oracle.security.am.asdk.AccessException;

/**
 * <p>
 * A node that authenticates the user in the OAM and retrieves an SSO token.
 * </p>
 */
@Node.Metadata(configClass = LegacyORALogin.LegacyORAConfig.class, outcomeProvider = AbstractLegacyLoginNode.OutcomeProvider.class)
public class LegacyORALogin extends AbstractLegacyLoginNode {

	private final Logger logger = LoggerFactory.getLogger(LegacyORALogin.class);
	private final LegacyORAConfig config;
	private final UUID nodeId;
	OracleService oracleService;

	/**
	 * Configuration for this node, as an extension from
	 * {@link AbstractLegacyLoginNode}
	 */
	public interface LegacyORAConfig extends AbstractLegacyLoginNode.Config {
	}

	/**
	 * Creates a LegacyORALogin node with the provided configuration
	 *
	 * @param realm           the tree's realm
	 * @param serviceRegistry instance of the tree's serice config.
	 * @param config          the configuration for this Node.
	 * @param nodeId          the ID of this node, used to bind the
	 *                        {@link LegacyORASessionTreeHook} execution at the end
	 *                        of the tree.
	 */
	@Inject
	public LegacyORALogin(@Assisted Realm realm, @Assisted LegacyORAConfig config, @Assisted UUID nodeId,
			AnnotatedServiceRegistry serviceRegistry) {
		this.config = config;
		this.nodeId = nodeId;
		try {
			oracleService = serviceRegistry.getRealmSingleton(OracleService.class, realm).get();
		} catch (SSOException | SMSException e) {
			logger.error("LegacyORALogin::constructor > SSOException | SMSException: ", e);
		}
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) {
		String username = context.sharedState.get(USERNAME).asString();
		String password = context.transientState.get(PASSWORD).asString();
		String responseCookie = getLegacyCookie(username, password);

		if (responseCookie != null) {
			logger.info("LegacyORALogin::process > Successful login in legacy system.");
			return goTo(true).putSessionProperty(SESSION_LEGACY_COOKIE, responseCookie)
					.putSessionProperty(SESSION_LEGACY_COOKIE_DOMAIN, oracleService.legacyCookieDomain())
					.putSessionProperty(SESSION_LEGACY_COOKIE_NAME, oracleService.legacyCookieName())
					.addSessionHook(LegacyORASessionTreeHook.class, nodeId, getClass().getSimpleName())
					.replaceSharedState(context.sharedState.put(LEGACY_COOKIE_SHARED_STATE_PARAM, responseCookie))
					.build();
		} else {
			logger.info("LegacyORALogin::process > Node outcome: FALSE");
			return goTo(false).build();
		}
	}

	/**
	 * Initializes communication with the legacy IAM and attempts to authenticate
	 * the user
	 *
	 * @param username the username that will be authenticated
	 * @param password the password of the user
	 * @return <b>null</b> if no cookie could be found following the authentication
	 *         request. Otherwise, return the OAM session cookie if successful.
	 */
	private String getLegacyCookie(String username, String password) {
		try {
			return ORAAccessClient.getInstance().authenticateUser(username, password, oracleService.msProtocol(),
					oracleService.msResource(), oracleService.msMethod(), oracleService.msConfigLocation());
		} catch (AccessException e) {
			logger.error("LegacyORALogin::getLegacyCookie > Error getting legacy SSO token: ", e);
		}
		return null;
	}
}