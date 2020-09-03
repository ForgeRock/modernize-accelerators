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

import static org.forgerock.openam.auth.node.api.SharedStateConstants.PASSWORD;
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
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE_DOMAIN;

import java.util.Enumeration;
import java.util.UUID;

import javax.inject.Inject;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractLegacyLoginNode;
import org.forgerock.openam.auth.node.treehook.LegacySessionTreeHook;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.modernize.legacy.SmSdkUtils;
import org.forgerock.openam.secrets.Secrets;
import org.forgerock.openam.secrets.SecretsProviderFacade;
import org.forgerock.secrets.NoSuchSecretException;
import org.forgerock.secrets.Purpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

import netegrity.siteminder.javaagent.AgentAPI;
import netegrity.siteminder.javaagent.AttributeList;
import netegrity.siteminder.javaagent.InitDef;
import netegrity.siteminder.javaagent.RealmDef;
import netegrity.siteminder.javaagent.ResourceContextDef;
import netegrity.siteminder.javaagent.ServerDef;
import netegrity.siteminder.javaagent.SessionDef;
import netegrity.siteminder.javaagent.UserCredentials;

/**
 * <p>
 * A node that authenticates the user in the legacy IAM and creates an SSO
 * token.
 * </p>
 */
@Node.Metadata(configClass = LegacySMLogin.LegacyFRConfig.class, outcomeProvider = AbstractLegacyLoginNode.OutcomeProvider.class)
public class LegacySMLogin extends AbstractLegacyLoginNode {
	private static final Logger LOGGER = LoggerFactory.getLogger(LegacySMLogin.class);
	private final LegacyFRConfig config;
	private final UUID nodeId;
	private String webAgentSecret;

	/**
	 * Configuration for this node, as an extension from
	 * {@link AbstractLegacyLoginNode}
	 */
	public interface LegacyFRConfig extends AbstractLegacyLoginNode.Config {

		/**
		 * Siteminder Policy Server IP address.
		 * 
		 * @return the configured policyServerIP
		 */
		@Attribute(order = 10, validators = { RequiredValueValidator.class })
		String policyServerIP();

		/**
		 * Siteminder Policy Server Accounting server port (0 for none). Mandatory if
		 * "Is 4x Web agent" config is activated.
		 * 
		 * @return the configured accountingPort
		 */
		@Attribute(order = 20)
		default int accountingPort() {
			return ACCOUNTING_PORT;
		}

		/**
		 * Siteminder Policy Server Authentication server port (0 for none). Mandatory
		 * if "Is 4x Web agent" config is activated.
		 * 
		 * @return the configured authenticationPort
		 */
		@Attribute(order = 30)
		default int authenticationPort() {
			return AUTHENTICATION_PORT;
		}

		/**
		 * Siteminder Policy Server Authorization server port (0 for none). Mandatory if
		 * "Is 4x Web agent" config is activated.
		 * 
		 * @return the configured authorizationPort
		 */
		@Attribute(order = 40)
		default int authorizationPort() {
			return AUTHORIZATION_PORT;
		}

		/**
		 * Number of initial connections. Mandatory if "Is 4x Web agent" config is
		 * activated.
		 * 
		 * @return the configured connectionMin value
		 */
		@Attribute(order = 50)
		default int connectionMin() {
			return CONNECTION_MIN;
		}

		/**
		 * Maximum number of connections. Mandatory if "Is 4x Web agent" config is
		 * activated.
		 * 
		 * @return the configured connectionMax value
		 */
		@Attribute(order = 60)
		default int connectionMax() {
			return CONNECTION_MAX;
		}

		/**
		 * Number of connections to allocate when out of connections. Mandatory if "Is
		 * 4x Web agent" config is activated.
		 * 
		 * @return the configured connectionStep value
		 */
		@Attribute(order = 70)
		default int connectionStep() {
			return CONNECTION_STEP;
		}

		/**
		 * Connection timeout in seconds. Mandatory if "Is 4x Web agent" config is
		 * activated.
		 * 
		 * @return the configured timeoutin seconds
		 */
		@Attribute(order = 80)
		default int timeout() {
			return CONNECTION_TIMEOUT;
		}

		/**
		 * The agent name. This name must match the agent name provided to the Policy
		 * Server. The agent name is not case sensitive.
		 * 
		 * @return the configured webAgentName
		 */
		@Attribute(order = 90, validators = { RequiredValueValidator.class })
		String webAgentName();

		/**
		 * The secret id of the AM secret that contains the web agent shared secret as
		 * defined in the SiteMinder user interface (case sensitive).
		 * 
		 * @return the configured webAgentSecretId
		 */
		@Attribute(order = 100)
		String webAgentPasswordSecretId();

		/**
		 * The version of web agent used by the siteminder policy server. Should be True
		 * if the "Is 4x" check box is active on the Siteminder Web Agent.
		 * 
		 * @return true if siteminder web agent version is 4x, false otherwise
		 */
		@Attribute(order = 110, validators = { RequiredValueValidator.class })
		default boolean is4xAgent() {
			return true;
		}

		/**
		 * Location on the AM instance, where the Siteminder web agent SmHost.conf file
		 * is located. Mandatory if "Is 4x Web agent" configuration is set to false
		 * (disabled).
		 * 
		 * @return configured smHostFilePath
		 */
		@Attribute(order = 120)
		String smHostFilePath();

		/**
		 * The name of the resource to check -- for example, /inventory/.
		 * 
		 * @return the configured protectedResource
		 */
		@Attribute(order = 130, validators = { RequiredValueValidator.class })
		String protectedResource();

		/**
		 * The action to check for the protected resource -- for example, GET.
		 * 
		 * @return the configured protectedResourceAction
		 */
		@Attribute(order = 140, validators = { RequiredValueValidator.class })
		default String protectedResourceAction() {
			return DEFAULT_ACTION;
		}

		/**
		 * A debug switch used to activate additional debug information.
		 * 
		 * @return configured debug value
		 */
		@Attribute(order = 150, validators = { RequiredValueValidator.class })
		default boolean debug() {
			return false;
		}

		/**
		 * Defines the domain for which the SSO token obtained from legacy OAM will be
		 * set
		 * 
		 * @return the value for the OAM cookie domain
		 */
		@Attribute(order = 160, validators = { RequiredValueValidator.class })
		String legacyCookieDomain();

	}

	/**
	 * Creates a LegacyFRLogin node with the provided configuration
	 * 
	 * @param config the configuration for this Node.
	 * @param nodeId the ID of this node, used to bind the
	 *               {@link LegacySessionTreeHook} execution at the end of the tree.
	 * @throws NodeProcessException
	 */
	@Inject
	public LegacySMLogin(@Assisted LegacyFRConfig config, @Assisted UUID nodeId, @Assisted Realm realm, Secrets secrets)
			throws NodeProcessException {
		this.config = config;
		this.nodeId = nodeId;
		SecretsProviderFacade secretsProvider = secrets.getRealmSecrets(realm);
		if (secretsProvider != null) {
			try {
				// non 4x web agent takes the secret from SmHost.conf file
				if (config.is4xAgent()) {
					this.webAgentSecret = secretsProvider
							.getNamedSecret(Purpose.PASSWORD, config.webAgentPasswordSecretId())
							.getOrThrowUninterruptibly().revealAsUtf8(String::valueOf).trim();
				}
			} catch (NoSuchSecretException e) {
				throw new NodeProcessException(
						"Check secret configurations for secret id's: " + config.webAgentPasswordSecretId());
			}
		}
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		LOGGER.info("LegacySMLogin::process() > Start");

		if (!SmSdkUtils.isNodeConfigurationValid(config.is4xAgent(), config.smHostFilePath(), config.accountingPort(),
				config.authenticationPort(), config.authorizationPort(), config.connectionMin(), config.connectionMax(),
				config.connectionStep(), config.timeout(), config.webAgentPasswordSecretId())) {
			throw new NodeProcessException(
					"LegacySMLogin::process: Configuration is not valid for the selected agent type");
		}

		// Initialize AgentAPI
		AgentAPI agentApi = new AgentAPI();
		ServerDef serverDefinition = null;
		InitDef initDefinition = new InitDef();

		String username = context.sharedState.get(USERNAME).asString();
		String password = context.transientState.get(PASSWORD).asString();

		// Create SM server and init definitions
		if (config.is4xAgent()) {
			serverDefinition = SmSdkUtils.createServerDefinition(config.policyServerIP(), config.connectionMin(),
					config.connectionMax(), config.connectionStep(), config.timeout(), config.authorizationPort(),
					config.authenticationPort(), config.accountingPort());
			initDefinition = SmSdkUtils.createInitDefinition(config.webAgentName(), webAgentSecret, false,
					serverDefinition);
		} else {
			LOGGER.info("LegacySMLogin::process() > Configuring AgentAPI for using a > 4.x web agent.");
			int configStatus = agentApi.getConfig(initDefinition, config.webAgentName(), config.smHostFilePath());
			LOGGER.info("LegacySMLogin::process() > getConfig returned status: {}", configStatus);
		}

		int retcode = agentApi.init(initDefinition);

		if (retcode != AgentAPI.SUCCESS) {
			LOGGER.error("LegacySMLogin::process() > AgentAPI init failed with return code: {}", retcode);
			throw new NodeProcessException("AgentAPI init failed with status: " + retcode);
		} else {
			if (config.debug()) {
				LOGGER.info("LegacySMLogin::process() > AgentAPI initialization SUCCESS.");
			}
		}

		// Initialize resource context and verify if the resource is protected
		ResourceContextDef resctxdef = new ResourceContextDef(config.webAgentName(), "", config.protectedResource(),
				config.protectedResourceAction());
		RealmDef realmdef = new RealmDef();
		retcode = agentApi.isProtected(config.policyServerIP(), resctxdef, realmdef);
		LOGGER.info("LegacySMLogin::process() > AgentAPI return code for isProtected(): {}", retcode);

		// Use the user credentials to authenticate on the protected resource
		UserCredentials userCredentials = new UserCredentials(username, password);
		SessionDef sessionDef = new SessionDef();
		AttributeList attrList = new AttributeList();

		retcode = agentApi.login(config.policyServerIP(), resctxdef, realmdef, userCredentials, sessionDef, attrList);

		if (retcode != AgentAPI.YES) {
			LOGGER.error("LegacySMLogin::process() > AgentAPI login failed with return code: {}", retcode);
			agentApi.unInit();
			throw new NodeProcessException("AgentAPI login failed with status: " + retcode);
		} else {
			if (config.debug()) {
				LOGGER.info("LegacySMLogin::process() > AgentAPI login SUCCESS.");
				LOGGER.info("LegacySMLogin::process() > SM session id: {}", sessionDef.id);
				LOGGER.info("LegacySMLogin::process() > SM session spec: {}", sessionDef.spec);
			}
		}

		@SuppressWarnings("unchecked")
		Enumeration<Attribute> attrListEnum = attrList.attributes();
		byte[] userDn = { 0 };

		while (attrListEnum.hasMoreElements()) {
			netegrity.siteminder.javaagent.Attribute attr = (netegrity.siteminder.javaagent.Attribute) attrListEnum
					.nextElement();

			if (attr.id == AgentAPI.ATTR_USERDN) {
				userDn = attr.value;
			}
		}

		// create attribute list for creating an SSO token
		AttributeList ssoAttrs = new AttributeList();
		ssoAttrs.addAttribute(AgentAPI.ATTR_USERNAME, 0, 0, "", username.getBytes());
		ssoAttrs.addAttribute(AgentAPI.ATTR_USERDN, 0, 0, "", userDn);

		StringBuffer ssoToken = new StringBuffer();
		retcode = agentApi.createSSOToken(sessionDef, ssoAttrs, ssoToken);

		// Release agent api
		agentApi.unInit();
		if (retcode != AgentAPI.SUCCESS) {
			LOGGER.error("LegacySMLogin::process() > AgentAPI createSSOToken failed with return code: {}", retcode);
			throw new NodeProcessException("AgentAPI createSSOToken failed with status: " + retcode);
		} else {
			if (config.debug()) {
				LOGGER.info("LegacySMLogin::process() > AgentAPI createSSOToken SUCCESS.");
				LOGGER.info("LegacySMLogin::process() > SMSESSION created: {}", ssoToken);
				LOGGER.info("LegacySMLogin::process() > Successfull login in legacy system.");
				SmSdkUtils.displayAttributes(ssoAttrs);
			}
			String legacyCookie = config.legacyCookieName() + "=" + ssoToken.toString();
			return goTo(true).putSessionProperty(SESSION_LEGACY_COOKIE_DOMAIN, config.legacyCookieDomain())
					.putSessionProperty(LEGACY_COOKIE_SHARED_STATE_PARAM, legacyCookie)
					.addSessionHook(LegacySessionTreeHook.class, nodeId, getClass().getSimpleName())
					.replaceSharedState(context.sharedState.put(LEGACY_COOKIE_SHARED_STATE_PARAM, legacyCookie))
					.build();
		}
	}
}