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
package org.forgerock.openam.auth.node;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.ACCOUNTING_PORT;
import static org.forgerock.openam.modernize.utils.NodeConstants.AUTHENTICATION_PORT;
import static org.forgerock.openam.modernize.utils.NodeConstants.AUTHORIZATION_PORT;
import static org.forgerock.openam.modernize.utils.NodeConstants.CONNECTION_MAX;
import static org.forgerock.openam.modernize.utils.NodeConstants.CONNECTION_MIN;
import static org.forgerock.openam.modernize.utils.NodeConstants.CONNECTION_STEP;
import static org.forgerock.openam.modernize.utils.NodeConstants.CONNECTION_TIMEOUT;
import static org.forgerock.openam.modernize.utils.NodeConstants.DEFAULT_ACTION;
import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;

import java.util.Enumeration;
import java.util.Map;

import javax.inject.Inject;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractValidateTokenNode;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.modernize.legacy.SmSdkUtils;
import org.forgerock.openam.secrets.Secrets;
import org.forgerock.openam.secrets.SecretsProviderFacade;
import org.forgerock.secrets.NoSuchSecretException;
import org.forgerock.secrets.Purpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.shared.xml.XMLUtils;
import com.sun.identity.sm.RequiredValueValidator;

import netegrity.siteminder.javaagent.AgentAPI;
import netegrity.siteminder.javaagent.InitDef;
import netegrity.siteminder.javaagent.ServerDef;
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

	public interface LegacyFRConfig extends AbstractValidateTokenNode.Config {

		@Attribute(order = 10, validators = { RequiredValueValidator.class })
		String policyServerIP();

		@Attribute(order = 20)
		default int accountingPort() {
			return ACCOUNTING_PORT;
		}

		@Attribute(order = 30)
		default int authenticationPort() {
			return AUTHENTICATION_PORT;
		}

		@Attribute(order = 40)
		default int authorizationPort() {
			return AUTHORIZATION_PORT;
		}

		@Attribute(order = 50)
		default int connectionMin() {
			return CONNECTION_MIN;
		}

		@Attribute(order = 60)
		default int connectionMax() {
			return CONNECTION_MAX;
		}

		@Attribute(order = 70)
		default int connectionStep() {
			return CONNECTION_STEP;
		}

		@Attribute(order = 80)
		default int timeout() {
			return CONNECTION_TIMEOUT;
		}

		@Attribute(order = 90, validators = { RequiredValueValidator.class })
		String webAgentName();

		@Attribute(order = 100)
		String webAgentSecret();

		@Attribute(order = 110, validators = { RequiredValueValidator.class })
		default boolean is4xAgent() {
			return true;
		}

		@Attribute(order = 120)
		String smHostFilePath();

		@Attribute(order = 130, validators = { RequiredValueValidator.class })
		String protectedResource();

		@Attribute(order = 140, validators = { RequiredValueValidator.class })
		default String protectedResourceAction() {
			return DEFAULT_ACTION;
		}

		@Attribute(order = 150, validators = { RequiredValueValidator.class })
		default boolean debug() {
			return false;
		}
	}

	/**
	 * Creates a LegacyFRValidateToken node with the provided configuration
	 * 
	 * @param config the configuration for this Node.
	 * @throws NodeProcessException
	 */
	@Inject
	public LegacySMValidateToken(@Assisted LegacyFRConfig config, @Assisted Realm realm, Secrets secrets)
			throws NodeProcessException {
		this.config = config;
		SecretsProviderFacade secretsProvider = secrets.getRealmSecrets(realm);
		if (secretsProvider != null) {
			try {
				// non 4x web agent takes the secret from SmHost.conf file
				if (config.is4xAgent()) {
					this.webAgentSecret = secretsProvider.getNamedSecret(Purpose.PASSWORD, config.webAgentSecret())
							.getOrThrowUninterruptibly().revealAsUtf8(String::valueOf).trim();
				}
			} catch (NoSuchSecretException e) {
				throw new NodeProcessException(
						"Check secret configurations for secret id's: " + config.webAgentSecret());
			}
		}
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		LOGGER.info("LegacySMValidateToken::process() > Start");

		if (!SmSdkUtils.isNodeConfigurationValid(config.is4xAgent(), config.smHostFilePath(), config.accountingPort(),
				config.authenticationPort(), config.authorizationPort(), config.connectionMin(), config.connectionMax(),
				config.connectionStep(), config.timeout(), config.webAgentSecret())) {
			throw new NodeProcessException(
					"LegacySMValidateToken::process: Configuration is not valid for the selected agent type");
		}

		Map<String, String> cookies = context.request.cookies;
		String smCookie = cookies.get(config.legacyCookieName());

		if (smCookie == null) {
			LOGGER.info("LegacySMValidateToken::process() > No SM Cookie Found");
			return goTo(false).build();
		}

		LOGGER.info("LegacySMValidateToken::process() > SM cookie found: {}", smCookie);

		// Initialize AgentAPI
		AgentAPI agentapi = new AgentAPI();
		ServerDef serverDefinition = null;
		InitDef initDefinition = new InitDef();

		// Create SM server and init definitions
		if (config.is4xAgent()) {
			serverDefinition = SmSdkUtils.createServerDefinition(config.policyServerIP(), config.connectionMin(),
					config.connectionMax(), config.connectionStep(), config.timeout(), config.authorizationPort(),
					config.authenticationPort(), config.accountingPort());
			initDefinition = SmSdkUtils.createInitDefinition(config.webAgentName(), webAgentSecret, false,
					serverDefinition);
		} else {
			LOGGER.info("LegacySMValidateToken::process() > Configuring AgentAPI for using a > 4.x web agent.");
			int configStatus = agentapi.getConfig(initDefinition, config.webAgentName(), config.smHostFilePath());
			LOGGER.info("LegacySMValidateToken::process() > getConfig returned status: {}", configStatus);
		}

		int retcode = agentapi.init(initDefinition);

		if (retcode == AgentAPI.SUCCESS) {
			LOGGER.info("LegacySMValidateToken::process() > SM AgentAPI init succesful");
		} else {
			LOGGER.info("LegacySMValidateToken::process() > SM AgentAPI init failed with status {}", retcode);
			agentapi.unInit();
			throw new NodeProcessException("SM AgentAPI init failed with status: " + retcode);
		}

		// Validate SM legacy token
		int version = 0;
		boolean thirdParty = false;
		TokenDescriptor tokenDescriptor = new TokenDescriptor(version, thirdParty);
		netegrity.siteminder.javaagent.AttributeList attributeList = new netegrity.siteminder.javaagent.AttributeList();
		StringBuffer token = new StringBuffer();
		int status = agentapi.decodeSSOToken(smCookie, tokenDescriptor, attributeList, false, token);

		LOGGER.info("LegacySMValidateToken::process()::Token status: {}", status);

		if (status == AgentAPI.SUCCESS) {
			LOGGER.info("LegacySMValidateToken::process() > SM session decoded succesfully");
		} else {
			LOGGER.info("LegacySMValidateToken::process() >  SM session decode failed with: status: {} for cookie {}",
					status, smCookie);
			agentapi.unInit();
			throw new NodeProcessException(
					"LegacySMValidateToken::process() > SMSession decode failed, status=" + status);
		}

		// Get SM user name and set it on the shared state along with the SM cookie
		@SuppressWarnings("rawtypes")
		Enumeration attributes = attributeList.attributes();
		while (attributes.hasMoreElements()) {
			netegrity.siteminder.javaagent.Attribute attr = (netegrity.siteminder.javaagent.Attribute) attributes
					.nextElement();
			int attrId = attr.id;
			if (attrId == AgentAPI.ATTR_USERNAME) {
				String smUserName = XMLUtils.removeNullCharAtEnd(new String(attr.value));
				LOGGER.info("LegacySMValidateToken::process() > smUserName: {}", smUserName);
				agentapi.unInit();
				return goTo(true).replaceSharedState(context.sharedState.copy().add(USERNAME, smUserName)
						.add(LEGACY_COOKIE_SHARED_STATE_PARAM, smCookie)).build();
			}
		}
		LOGGER.error("LegacySMValidateToken::process() > Did not find SM user name. Destroying AgentAPI");
		agentapi.unInit();
		return goTo(false).build();

	}

}