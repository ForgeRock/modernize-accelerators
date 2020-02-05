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

import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;

import javax.inject.Inject;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractValidateTokenNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

import oracle.security.am.asdk.AccessClient;
import oracle.security.am.asdk.AccessException;
import oracle.security.am.asdk.UserSession;

/**
 * 
 * <p>
 * This node validates if the user accessed the tree holding a legacy IAM SSO
 * Token. If the session is valid, the node also saves on the shared state the
 * legacy cookie identified, and the username associated to that cookie in the
 * legacy iAM.
 * </p>
 *
 */
@Node.Metadata(configClass = LegacyORAValidateToken.LegacyORAConfig.class, outcomeProvider = AbstractValidateTokenNode.OutcomeProvider.class)
public class LegacyORAValidateToken extends AbstractDecisionNode {

	private Logger LOGGER = LoggerFactory.getLogger(LegacyORAValidateToken.class);
	private final LegacyORAConfig config;

	public interface LegacyORAConfig extends AbstractValidateTokenNode.Config {

		@Attribute(order = 10, validators = { RequiredValueValidator.class })
		String msConfigLocation();

		@Attribute(order = 20, validators = { RequiredValueValidator.class })
		String namingAttribute();
	}

	@Inject
	public LegacyORAValidateToken(@Assisted LegacyORAConfig config) {
		this.config = config;
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String legacyCookie = context.request.cookies.get(config.legacyCookieName());
		LOGGER.debug("process()::legacyCookie: " + legacyCookie);
		String uid = validateLegacySession(legacyCookie);
		LOGGER.debug("process()::User id from legacy cookie: " + uid);
		if (uid != null && legacyCookie != null) {
			if (!legacyCookie.contains(config.legacyCookieName())) {
				legacyCookie = config.legacyCookieName() + "=" + legacyCookie;
			}
			return goTo(true)
					.replaceSharedState(
							context.sharedState.add(USERNAME, uid).add(LEGACY_COOKIE_SHARED_STATE_PARAM, legacyCookie))
					.build();
		}
		return goTo(false).build();
	}

	/**
	 * 
	 * Validates a legacy iAM cookie by calling the session validation end point.
	 * 
	 * @param legacyCookie
	 * @return the user id if the session is valid, or null if the session is
	 *         invalid or something went wrong.
	 */
	private String validateLegacySession(String legacyCookie) {
		if (legacyCookie != null && legacyCookie.length() > 0) {
			AccessClient ac = null;
			try {
				ac = AccessClient.createDefaultInstance(config.msConfigLocation(),
						AccessClient.CompatibilityMode.OAM_10G);
				UserSession session = new UserSession(ac, legacyCookie);
				LOGGER.debug("Session status: " + session.getStatus());
				String userDn = session.getUserIdentity();
				LOGGER.debug("User identity: " + userDn);
				String[] dnParts = userDn.split(",");
				for (String part : dnParts) {
					if (part.contains(config.namingAttribute() + "=")) {
						return part.split("=")[1];
					}
				}
			} catch (AccessException ae) {
				LOGGER.error("Access Exception: " + ae.getMessage());
				ae.printStackTrace();
			}
			ac.shutdown();
		}
		return null;
	}

}