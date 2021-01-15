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

import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;

import javax.inject.Inject;

import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractValidateTokenNode;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.services.OracleService;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

import oracle.security.am.asdk.AccessClient;
import oracle.security.am.asdk.AccessException;
import oracle.security.am.asdk.UserSession;

/**
 * <p>
 * A node which validates if the user accessing the tree is having a legacy IAM
 * SSO Token. If the session is validated successfully, the node also saves on
 * the shared state the legacy cookie identified, and the username associated to
 * that cookie in the legacy IAM.
 * </p>
 */
@Node.Metadata(configClass = LegacyORAValidateToken.LegacyORAConfig.class, outcomeProvider = AbstractValidateTokenNode.OutcomeProvider.class)
public class LegacyORAValidateToken extends AbstractDecisionNode {

	private final Logger logger = LoggerFactory.getLogger(LegacyORAValidateToken.class);
	private final LegacyORAConfig config;
	OracleService oracleService;

	public interface LegacyORAConfig extends AbstractValidateTokenNode.Config {
	}

	/**
	 * Node's constructor
	 * 
	 * @param realm           the current realm of the node.
	 * @param config          the node's configuration
	 * @param serviceRegistry instance of the tree's serice config.
	 */
	@Inject
	public LegacyORAValidateToken(@Assisted Realm realm, @Assisted LegacyORAConfig config,
			AnnotatedServiceRegistry serviceRegistry) {
		this.config = config;
		try {
			oracleService = serviceRegistry.getRealmSingleton(OracleService.class, realm).get();
		} catch (SSOException | SMSException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) {

		String legacyCookie = context.request.cookies.get(oracleService.legacyCookieName());
		logger.info("LegacyORAValidateToken::process > legacyCookie: {}", legacyCookie);
		String uid = validateLegacySession(legacyCookie);

		if (uid != null) {
			if (!legacyCookie.contains(oracleService.legacyCookieName())) {
				legacyCookie = oracleService.legacyCookieName() + "=" + legacyCookie;
			}
			return goTo(true)
					.replaceSharedState(
							context.sharedState.add(USERNAME, uid).add(LEGACY_COOKIE_SHARED_STATE_PARAM, legacyCookie))
					.build();
		}

		logger.info("LegacyORAValidateToken::process > Node outcome: FALSE");
		return goTo(false).build();
	}

	/**
	 * Validates an OAM cookie by calling the session validation endpoint.
	 *
	 * @param legacyCookie the ORA legacy cookie
	 * @return the user id if the session is valid, or null if the session is
	 *         invalid or something went wrong.
	 */
	private String validateLegacySession(String legacyCookie) {
		if (legacyCookie != null && legacyCookie.length() > 0) {
			AccessClient ac = null;

			try {
				ac = AccessClient.createDefaultInstance(oracleService.msConfigLocation(),
						AccessClient.CompatibilityMode.OAM_10G);
				UserSession session = new UserSession(ac, legacyCookie);
				logger.info("LegacyORAValidateToken::validateLegacySession > Session status: {}", session.getStatus());
				String userDn = session.getUserIdentity();
				String[] dnParts = userDn.split(",");

				for (String part : dnParts) {
					if (part.contains(oracleService.namingAttribute() + "=")) {
						return part.split("=")[1];
					}
				}
			} catch (AccessException ae) {
				logger.error("LegacyORAValidateToken::validateLegacySession > Access Exception: {0}", ae);
			}
			if (ac != null)
				ac.shutdown();
		}
		return null;
	}
}