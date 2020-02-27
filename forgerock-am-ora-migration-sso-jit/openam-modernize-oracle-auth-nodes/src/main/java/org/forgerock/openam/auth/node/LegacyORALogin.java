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
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE;
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE_DOMAIN;
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE_NAME;

import java.util.UUID;

import javax.inject.Inject;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractLegacyLoginNode;
import org.forgerock.openam.auth.node.treehook.LegacyORASessionTreeHook;
import org.forgerock.openam.modernize.legacy.ORAAccessClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

import oracle.security.am.asdk.AccessException;

/**
 * <p>
 * A node that authenticates the user in the OAM and retrieves an SSO token.
 * </p>
 */
@Node.Metadata(configClass = LegacyORALogin.LegacyORAConfig.class, outcomeProvider = AbstractLegacyLoginNode.OutcomeProvider.class)
public class LegacyORALogin extends AbstractLegacyLoginNode {

	private Logger LOGGER = LoggerFactory.getLogger(LegacyORALogin.class);
	private final LegacyORAConfig config;
	private final UUID nodeId;

	/**
	 * Configuration for this node, as an extension from
	 * {@link AbstractLegacyLoginNode}
	 */
	public interface LegacyORAConfig extends AbstractLegacyLoginNode.Config {

		/**
		 * Defines the name of the resource accessed. Since the requested resource type
		 * for this particular example is HTTP, it is legal to prepend a host name and
		 * port number to the resource name, as in the following example:
		 * 
		 * <pre>
		 * //Example.com:80/resource/index.html
		 * </pre>
		 * 
		 * @return the OAM login endpoint (resource)
		 */
		@Attribute(order = 10, validators = { RequiredValueValidator.class })
		String msResource();

		/**
		 * Defines the type of resource being requested. Example:
		 * 
		 * <pre>
		 * HTTPS
		 * </pre>
		 * 
		 * @return the type of resource being accessed
		 */
		@Attribute(order = 20, validators = { RequiredValueValidator.class })
		String msProtocol();

		/**
		 * Defines which is the type of operation to be performed against the resource.
		 * When the resource type is HTTP, the possible operations are GET and POST
		 * 
		 * @return the type of operation to perform
		 */
		@Attribute(order = 30, validators = { RequiredValueValidator.class })
		String msMethod();

		/**
		 * Defines the domain for which the SSO token obtained from legacy OAM will be
		 * set
		 * 
		 * @return the value for the OAM cookie domain
		 */
		@Attribute(order = 40, validators = { RequiredValueValidator.class })
		String legacyCookieDomain();

		/**
		 * Defines the path where the OAM ObAccessClient.xml file is configured
		 * 
		 * @return the configured path
		 */
		@Attribute(order = 50, validators = { RequiredValueValidator.class })
		String msConfigLocation();

	}

	/**
	 * Creates a LegacyORALogin node with the provided configuration
	 * 
	 * @param config the configuration for this Node.
	 * @param nodeId the ID of this node, used to bind the
	 *               {@link LegacyORASessionTreeHook} execution at the end of the
	 *               tree.
	 */
	@Inject
	public LegacyORALogin(@Assisted LegacyORAConfig config, @Assisted UUID nodeId) {
		this.config = config;
		this.nodeId = nodeId;
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String username = context.sharedState.get(USERNAME).asString();
		String password = context.transientState.get(PASSWORD).asString();
		String responseCookie = getLegacyCookie(username, password);
		if (responseCookie != null) {
			LOGGER.info("process(): Successfull login in legacy system.");
			return goTo(true).putSessionProperty(SESSION_LEGACY_COOKIE, responseCookie)
					.putSessionProperty(SESSION_LEGACY_COOKIE_DOMAIN, config.legacyCookieDomain())
					.putSessionProperty(SESSION_LEGACY_COOKIE_NAME, config.legacyCookieName())
					.addSessionHook(LegacyORASessionTreeHook.class, nodeId, getClass().getSimpleName())
					.replaceSharedState(context.sharedState.put(LEGACY_COOKIE_SHARED_STATE_PARAM, responseCookie))
					.build();
		} else {
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
			return ORAAccessClient.getInstance().authenticateUser(username, password, config.msProtocol(),
					config.msResource(), config.msMethod(), config.msConfigLocation());
		} catch (AccessException e) {
			LOGGER.error("getLegacyCookie()::Error getting legacy SSO token: " + e);
		}
		return null;
	}
}