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
import org.forgerock.openam.services.SiteminderService;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.forgerock.secrets.NoSuchSecretException;
import org.forgerock.secrets.Purpose;
import org.forgerock.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

import netegrity.siteminder.javaagent.AgentAPI;
import netegrity.siteminder.javaagent.AttributeList;
import netegrity.siteminder.javaagent.RealmDef;
import netegrity.siteminder.javaagent.ResourceContextDef;
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
	SiteminderService siteminderService;

	/**
	 * Configuration for this node, as an extension from
	 * {@link AbstractLegacyLoginNode}
	 */
	public interface LegacyFRConfig extends AbstractLegacyLoginNode.Config {
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
	public LegacySMLogin(@Assisted LegacyFRConfig config, @Assisted UUID nodeId, @Assisted Realm realm, Secrets secrets,
			AnnotatedServiceRegistry serviceRegistry) throws NodeProcessException {
		this.config = config;
		this.nodeId = nodeId;
		SecretsProviderFacade secretsProvider = secrets.getRealmSecrets(realm);
		try {
			siteminderService = serviceRegistry.getRealmSingleton(SiteminderService.class, realm).get();
		} catch (SSOException | SMSException e) {
			LOGGER.error("LegacySMLogin::constructor > SSOException | SMSException: ", e);
		}
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
						"LegacySMLogin::LegacySMLogin > Check secret configurations for secret id's");
			}
		}
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		LOGGER.info("LegacySMLogin::process > Start");

		if (!SmSdkUtils.isNodeConfigurationValid(siteminderService)) {
			throw new NodeProcessException(
					"LegacySMLogin::process > Configuration is not valid for the selected agent type");
		}

		String username = context.sharedState.get(USERNAME).asString();
		String password = context.transientState.get(PASSWORD).asString();

		AgentAPI agentAPI = SmSdkUtils.initConnectionAgent(siteminderService, webAgentSecret);
		Pair<SessionDef, AttributeList> session = getSession(agentAPI, username, password);

		if (session == null) {
			return goTo(false).build();
		}

		SessionDef sessionDef = session.getFirst();
		AttributeList attrList = session.getSecond();

		return getToken(agentAPI, sessionDef, attrList, context).build();
	}

	/**
	 * Logs into the user's account using the given credentials
	 *
	 * @param agentAPI successfully initialized AgentAPI instance
	 * @param username user's name used to authenticate
	 * @param password user's password used to authenticate
	 * @return retrieves the attribute list and the session of the user after
	 *         successfully logging in with the given credentials; null if couldn't
	 *         log in
	 */
	private Pair<SessionDef, AttributeList> getSession(AgentAPI agentAPI, String username, String password) {
		int retCode;

		// Initialize resource context and verify if the resource is protected
		ResourceContextDef resCtxDef = new ResourceContextDef(siteminderService.webAgentName(), "",
				siteminderService.protectedResource(), siteminderService.protectedResourceAction());

		RealmDef realmdef = new RealmDef();
		retCode = agentAPI.isProtected(siteminderService.policyServerIP(), resCtxDef, realmdef);
		LOGGER.info("LegacySMLogin::process > AgentAPI return code for isProtected(): {}", retCode);

		// Use the user credentials to authenticate on the protected resource
		UserCredentials userCredentials = new UserCredentials(username, password);
		SessionDef sessionDef = new SessionDef();
		AttributeList attrList = new AttributeList();

		retCode = agentAPI.login(siteminderService.policyServerIP(), resCtxDef, realmdef, userCredentials, sessionDef,
				attrList);
		if (retCode != AgentAPI.YES) {
			LOGGER.error("LegacySMLogin::process > AgentAPI login failed with return code: {}", retCode);

			agentAPI.unInit();
			return null;
		} else if (Boolean.TRUE.equals(siteminderService.debug())) {
			LOGGER.info("LegacySMLogin::process > AgentAPI login SUCCESS.");
			LOGGER.info("LegacySMLogin::process > SM session id: {}", sessionDef.id);
			LOGGER.info("LegacySMLogin::process > SM session spec: {}", sessionDef.spec);
		}

		return Pair.of(sessionDef, attrList);
	}

	/**
	 * Creates a SSO token and stores on the shared state the cookie
	 *
	 * @param agentAPI   authenticated and successfully initialized AgentAPI
	 *                   instance
	 * @param sessionDef SessionDef instance obtained after the successful login
	 * @param attrList   attribute list obtained after the successful login
	 * @param context    current's node context
	 * @return node's unbuilt outcome
	 */
	private Action.ActionBuilder getToken(AgentAPI agentAPI, SessionDef sessionDef, AttributeList attrList,
			TreeContext context) {
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
		String username = context.sharedState.get(USERNAME).asString();
		ssoAttrs.addAttribute(AgentAPI.ATTR_USERNAME, 0, 0, "", username.getBytes());
		ssoAttrs.addAttribute(AgentAPI.ATTR_USERDN, 0, 0, "", userDn);

		StringBuffer ssoToken = new StringBuffer();
		int retCode = agentAPI.createSSOToken(sessionDef, ssoAttrs, ssoToken);

		// Release agent api
		agentAPI.unInit();
		if (retCode != AgentAPI.SUCCESS) {
			LOGGER.error("LegacySMLogin::process > AgentAPI createSSOToken failed with return code: {}", retCode);
			return goTo(false);
		} else {
			if (Boolean.TRUE.equals(siteminderService.debug())) {
				LOGGER.info("LegacySMLogin::process > AgentAPI createSSOToken SUCCESS.");
				LOGGER.info("LegacySMLogin::process > SMSESSION created: {}", ssoToken);
				LOGGER.info("LegacySMLogin::process > Successfully login in legacy system.");
				SmSdkUtils.displayAttributes(ssoAttrs);
			}
			String legacyCookie = siteminderService.legacyCookieName() + "=" + ssoToken.toString();
			return goTo(true).putSessionProperty(SESSION_LEGACY_COOKIE_DOMAIN, siteminderService.legacyCookieDomain())
					.putSessionProperty(LEGACY_COOKIE_SHARED_STATE_PARAM, legacyCookie)
					.addSessionHook(LegacySessionTreeHook.class, nodeId, getClass().getSimpleName())
					.replaceSharedState(context.sharedState.put(LEGACY_COOKIE_SHARED_STATE_PARAM, legacyCookie));
		}
	}
}