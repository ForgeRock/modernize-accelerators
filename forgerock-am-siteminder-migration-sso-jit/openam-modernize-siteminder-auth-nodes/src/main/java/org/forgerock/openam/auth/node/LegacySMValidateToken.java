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

import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;

import java.util.Enumeration;
import java.util.Map;

import javax.inject.Inject;

import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractValidateTokenNode;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.modernize.legacy.SmSdkUtils;
import org.forgerock.openam.secrets.Secrets;
import org.forgerock.openam.secrets.SecretsProviderFacade;
import org.forgerock.openam.services.SiteminderService;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.forgerock.secrets.NoSuchSecretException;
import org.forgerock.secrets.Purpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.shared.xml.XMLUtils;
import com.sun.identity.sm.SMSException;

import netegrity.siteminder.javaagent.AgentAPI;
import netegrity.siteminder.javaagent.TokenDescriptor;

/**
 * <p>
 * A node which validates if the user accessing the tree is having a legacy IAM
 * SSO Token. If the session is validated successfully, the node also saves on
 * the shared state the legacy cookie identified, and the user name associated
 * to that cookie in the legacy IAM.
 * </p>
 */
@Node.Metadata(configClass = LegacySMValidateToken.LegacyFRConfig.class, outcomeProvider = AbstractValidateTokenNode.OutcomeProvider.class)
public class LegacySMValidateToken extends AbstractValidateTokenNode {

	private static final Logger LOGGER = LoggerFactory.getLogger(LegacySMValidateToken.class);
	private final LegacyFRConfig config;
	private String webAgentSecret;
	SiteminderService siteminderService;

	/**
	 * The node configuration
	 */
	public interface LegacyFRConfig extends AbstractValidateTokenNode.Config {

	}

	/**
	 * Creates a LegacyFRValidateToken node with the provided configuration
	 *
	 * @param config the configuration for this Node.
	 * @throws NodeProcessException when an exception occurs
	 */
	@Inject
	public LegacySMValidateToken(@Assisted LegacyFRConfig config, @Assisted Realm realm, Secrets secrets,
			AnnotatedServiceRegistry serviceRegistry) throws NodeProcessException {
		this.config = config;
		try {
			siteminderService = serviceRegistry.getRealmSingleton(SiteminderService.class, realm).get();
		} catch (SSOException | SMSException e) {
			LOGGER.error("LegacySMLogin::constructor > SSOException | SMSException: ", e);
		}
		SecretsProviderFacade secretsProvider = secrets.getRealmSecrets(realm);
		if (secretsProvider != null) {
			try {
				// non 4x web agent takes the secret from SmHost.conf file
				if (Boolean.TRUE.equals(siteminderService.is4xAgent())) {
					this.webAgentSecret = secretsProvider
							.getNamedSecret(Purpose.PASSWORD, siteminderService.webAgentPasswordSecretId())
							.getOrThrowIfInterrupted().revealAsUtf8(String::valueOf).trim();
				}
			} catch (NoSuchSecretException e) {
				throw new NodeProcessException(
						"LegacySMValidateToken::LegacySMValidateToken > Check secret configurations for secret id's");
			}
		}
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		LOGGER.info("LegacySMValidateToken::process > Start");

		if (!SmSdkUtils.isNodeConfigurationValid(siteminderService)) {
			throw new NodeProcessException(
					"LegacySMValidateToken::process > Configuration is not valid for the selected agent type");
		}

		Map<String, String> cookies = context.request.cookies;
		String smCookie = cookies.get(siteminderService.legacyCookieName());

		if (smCookie == null) {
			LOGGER.info("LegacySMValidateToken::process > No SM Cookie Found");
			return goTo(false).build();
		}

		AgentAPI agentAPI = SmSdkUtils.initConnectionAgent(siteminderService, webAgentSecret);
		String uid = validateLegacySession(agentAPI, smCookie);

		if (uid != null) {
			// Manage cookie name if absent
			if (!smCookie.contains(siteminderService.legacyCookieName())) {
				smCookie = siteminderService.legacyCookieName() + "=" + smCookie;
			}

			// Put uid and cookie on shared state
			return goTo(true)
					.replaceSharedState(
							context.sharedState.put(USERNAME, uid).put(LEGACY_COOKIE_SHARED_STATE_PARAM, smCookie))
					.build();
		}

		return goTo(false).build();
	}

	/**
	 * Validates a legacy IAM cookie by calling the session validation endpoint.
	 *
	 * @param legacyCookie the user's legacy SSO token
	 * @return the user id if the session is valid, or <b>null</b> if the session is
	 *         invalid or something unexpected happened.
	 */
	public String validateLegacySession(AgentAPI agentAPI, String legacyCookie) {
		// Validate SM legacy token
		TokenDescriptor tokenDescriptor = new TokenDescriptor(0, false);
		StringBuffer token = new StringBuffer();
		String smUserName = null;

		if (agentAPI == null) {
			LOGGER.error("LegacySMValidateToken::process > Couldn't init agentAPI.");
			return null;
		}

		netegrity.siteminder.javaagent.AttributeList attributeList = new netegrity.siteminder.javaagent.AttributeList();
		int status = agentAPI.decodeSSOToken(legacyCookie, tokenDescriptor, attributeList, false, token);
		LOGGER.info("LegacySMValidateToken::process > Token status: {}", status);
		if (status == AgentAPI.SUCCESS) {
			LOGGER.info("LegacySMValidateToken::process > SM session decoded successfully");
		} else {
			LOGGER.error("LegacySMValidateToken::process > SM session decode failed with: status: {} for cookie {}",
					status, legacyCookie);

			agentAPI.unInit();
			return null;
		}

		// Get SM user name
		@SuppressWarnings("rawtypes")
		Enumeration attributes = attributeList.attributes();
		while (attributes.hasMoreElements()) {
			netegrity.siteminder.javaagent.Attribute attr = (netegrity.siteminder.javaagent.Attribute) attributes
					.nextElement();
			int attrId = attr.id;
			if (attrId == AgentAPI.ATTR_USERNAME) {
				smUserName = XMLUtils.removeNullCharAtEnd(new String(attr.value));
				break;
			}
		}

		LOGGER.info("LegacySMValidateToken::process > SM user name: {}", smUserName);
		agentAPI.unInit();

		return smUserName;
	}
}