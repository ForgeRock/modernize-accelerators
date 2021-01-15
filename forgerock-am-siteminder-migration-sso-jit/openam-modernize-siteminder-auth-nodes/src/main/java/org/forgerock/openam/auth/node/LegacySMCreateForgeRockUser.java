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

import java.util.Map;
import java.util.Vector;

import javax.inject.Inject;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractLegacyCreateForgeRockUserNode;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.modernize.legacy.SmSdkUtils;
import org.forgerock.openam.modernize.utils.LegacySMVObjectAttributesHandler;
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
import com.netegrity.sdk.apiutil.SmApiConnection;
import com.netegrity.sdk.apiutil.SmApiException;
import com.netegrity.sdk.apiutil.SmApiResult;
import com.netegrity.sdk.apiutil.SmApiSession;
import com.netegrity.sdk.dmsapi.SmDmsApi;
import com.netegrity.sdk.dmsapi.SmDmsApiImpl;
import com.netegrity.sdk.dmsapi.SmDmsConfig;
import com.netegrity.sdk.dmsapi.SmDmsDirectory;
import com.netegrity.sdk.dmsapi.SmDmsDirectoryContext;
import com.netegrity.sdk.dmsapi.SmDmsObject;
import com.netegrity.sdk.dmsapi.SmDmsOrganization;
import com.netegrity.sdk.dmsapi.SmDmsSearch;
import com.netegrity.sdk.policyapi.SmPolicyApi;
import com.netegrity.sdk.policyapi.SmPolicyApiImpl;
import com.netegrity.sdk.policyapi.SmUserDirectory;
import com.sun.identity.sm.RequiredValueValidator;
import com.sun.identity.sm.SMSException;

import netegrity.siteminder.javaagent.AgentAPI;
import netegrity.siteminder.javaagent.InitDef;
import netegrity.siteminder.javaagent.ServerDef;

/**
 * <p>
 * A node which creates a user in ForgeRock IDM by calling the user endpoint
 * with the action query parameter set to create:
 * <b><i>{@code ?_action=create}</i></b>.
 * </p>
 */
@Node.Metadata(configClass = LegacySMCreateForgeRockUser.LegacyFRConfig.class, outcomeProvider = AbstractLegacyCreateForgeRockUserNode.OutcomeProvider.class)
public class LegacySMCreateForgeRockUser extends AbstractLegacyCreateForgeRockUserNode {

	private static final Logger logger = LoggerFactory.getLogger(LegacySMCreateForgeRockUser.class);
	private final LegacyFRConfig config;

	private String webAgentSecret;
	private String smAdminPassword;
	LegacySMVObjectAttributesHandler legacySMVObjectAttributesHandler;
	SiteminderService siteminderService;

	/**
	 * Node configuration
	 */
	public interface LegacyFRConfig extends AbstractLegacyCreateForgeRockUserNode.Config {

		/**
		 * A map which should hold as keys the name of the SiteMinder user attributes,
		 * and as values their equivalent name in the ForgeRock IDM database.
		 *
		 * @return the configured attributes map
		 */
		@Attribute(order = 250, validators = { RequiredValueValidator.class })
		Map<String, String> migrationAttributesMap();

	}

	/**
	 * Creates a LegacySMCreateForgeRockUser node with the provided configuration
	 *
	 * @param config  the configuration for this Node.
	 * @param realm   the realm the node is accessed from.
	 * @param secrets the secret store used to get passwords
	 * @throws NodeProcessException If there is an error reading the configuration.
	 * @throws NodeProcessException when an exception occurs
	 */
	@Inject
	public LegacySMCreateForgeRockUser(@Assisted LegacyFRConfig config, @Assisted Realm realm, Secrets secrets,
			AnnotatedServiceRegistry serviceRegistry) throws NodeProcessException {
		this.config = config;
		this.legacySMVObjectAttributesHandler = LegacySMVObjectAttributesHandler.getInstance();
		SecretsProviderFacade secretsProvider = secrets.getRealmSecrets(realm);
		try {
			siteminderService = serviceRegistry.getRealmSingleton(SiteminderService.class, realm).get();
		} catch (SSOException | SMSException e) {
			e.printStackTrace();
		}
		if (secretsProvider != null) {
			try {
				// non 4x web agent takes the secret from SmHost.conf file
				if (Boolean.TRUE.equals(siteminderService.is4xAgent())) {
					this.webAgentSecret = secretsProvider
							.getNamedSecret(Purpose.PASSWORD, siteminderService.webAgentPasswordSecretId())
							.getOrThrowIfInterrupted().revealAsUtf8(String::valueOf).trim();
				}
				this.smAdminPassword = secretsProvider
						.getNamedSecret(Purpose.PASSWORD, siteminderService.smAdminPasswordSecretId())
						.getOrThrowIfInterrupted().revealAsUtf8(String::valueOf).trim();
			} catch (NoSuchSecretException e) {
				throw new NodeProcessException("Check secret configurations for secret id's");
			}
		}
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		logger.info("LegacySMCreateForgeRockUser::process > Start");

		if (!SmSdkUtils.isNodeConfigurationValid(siteminderService)) {
			throw new NodeProcessException(
					"LegacySMLogin::process > Configuration is not valid for the selected agent type");
		}

		String userName = context.sharedState.get(USERNAME).asString();
		Map<String, String> userAttributes;
		try {
			userAttributes = getUserAttributes(userName);

			if (userAttributes != null) {
				return updateStates(context, userName, userAttributes);
			}
		} catch (SmApiException e) {
			throw new NodeProcessException("LegacySMCreateForgeRockUser::process > SmApiException: " + e);
		}

		return goTo(false).build();
	}

	/**
	 * Updates both the sharedState and the transientState (if it's the case)
	 * SharedState will receive an OBJECT_ATTRIBUTES object which will contain all
	 * the relevant info for the specified user TransientState will receive a
	 * password set for the specified user
	 *
	 * @param context  the tree context
	 * @param userName the username of our current user
	 * @param entity   the response body
	 * @return the action
	 */
	public Action updateStates(TreeContext context, String userName, Map<String, String> entity) {
		JsonValue userAttributes;
		JsonValue copySharedState = context.sharedState.copy();
		userAttributes = legacySMVObjectAttributesHandler.updateObjectAttributes(userName, entity, copySharedState);

		if (userAttributes != null) {
			if (config.setPasswordReset()) {
				JsonValue userAttributesTransientState = legacySMVObjectAttributesHandler.setPassword(context);

				if (userAttributesTransientState != null) {
					logger.info("LegacySMCreateForgeRockUser::updateStates > "
							+ "OBJECT_ATTRIBUTES added on shared state and on transient state.");
					return goTo(true).replaceSharedState(context.sharedState.put(OBJECT_ATTRIBUTES, userAttributes))
							.replaceTransientState(
									context.transientState.put(OBJECT_ATTRIBUTES, userAttributesTransientState))
							.build();
				}
			}

			logger.info("LegacySMCreateForgeRockUser::updateStates > OBJECT_ATTRIBUTES added on shared state.");
			return goTo(true).replaceSharedState(context.sharedState.put(OBJECT_ATTRIBUTES, userAttributes)).build();
		} else if (config.setPasswordReset()) {
			JsonValue userAttributesTransientState = legacySMVObjectAttributesHandler.setPassword(context);

			if (userAttributesTransientState != null) {
				logger.info("LegacySMCreateForgeRockUser::updateStates > OBJECT_ATTRIBUTES added on transient state.");
				return goTo(true).replaceTransientState(
						context.transientState.put(OBJECT_ATTRIBUTES, userAttributesTransientState)).build();
			}
		}

		return goTo(false).build();
	}

	/**
	 * Gets a user's attributes from the Siteminder directory.
	 *
	 * @param userName the user retrieved from the shared state
	 * @return a map of user attributes, in the format expected by ForgeRock IDM
	 * @throws SmApiException when an exception occurs
	 */
	private Map<String, String> getUserAttributes(String userName) throws SmApiException {
		// Initialize AgentAPI
		AgentAPI agentapi = new AgentAPI();
		ServerDef serverDefinition;
		InitDef initDefinition = new InitDef();

		// Create SM server and init definitions
		if (siteminderService.is4xAgent()) {
			logger.info(
					"LegacySMCreateForgeRockUser::getUserAttributes > Configuring AgentAPI for using a 4.x web agent.");
			serverDefinition = SmSdkUtils.createServerDefinition(siteminderService);
			initDefinition = SmSdkUtils.createInitDefinition(siteminderService.webAgentName(), webAgentSecret, false,
					serverDefinition);
		} else {
			logger.info(
					"LegacySMCreateForgeRockUser::getUserAttributes > Configuring AgentAPI for using a > 4.x web agent.");
			int configStatus = agentapi.getConfig(initDefinition, siteminderService.webAgentName(),
					siteminderService.smHostFilePath());
			logger.info("LegacySMCreateForgeRockUser::getUserAttributes > getConfig returned status: {}", configStatus);
		}

		int retCode = agentapi.init(initDefinition);

		if (retCode != AgentAPI.SUCCESS) {
			logger.error("LegacySMCreateForgeRockUser::getUserAttributes > AgentAPI init failed with return code: {}",
					retCode);
			return null;
		} else if (siteminderService.debug()) {
			logger.info("LegacySMCreateForgeRockUser::getUserAttributes > AgentAPI init SUCCESS.");
		}

		// Connection to the policy server
		return connectToThePolicyServer(agentapi, userName);
	}

	/**
	 * Connect to the policy server and get a user's attributes from the Siteminder
	 * directory.
	 *
	 * @param agent    the agent API
	 * @param userName the user retrieved from the shared state
	 * @return a map of user attributes, in the format expected by ForgeRock IDM
	 * @throws SmApiException when an exception occurs
	 */
	public Map<String, String> connectToThePolicyServer(AgentAPI agent, String userName) throws SmApiException {
		SmApiConnection apiConnection = new SmApiConnection(agent);
		SmApiSession apiSession = new SmApiSession(apiConnection);
		boolean loginResult = SmSdkUtils.adminLogin(apiSession, siteminderService.smAdminUser(),
				smAdminPassword.toCharArray());
		logger.info("LegacySMCreateForgeRockUser::connectToThePolicyServer > adminLogin result: {}", loginResult);

		// Get a list of user directories the admin can manage.
		SmPolicyApi policyApi = new SmPolicyApiImpl(apiSession);
		Vector<Object> userDirs = new Vector<>();

		// Returns the list of directory names.
		SmApiResult result = policyApi.getAdminUserDirs(siteminderService.smAdminUser(), userDirs);
		if (siteminderService.debug()) {
			SmSdkUtils.printObject(userDirs, result);
		}

		// Check if the USER_DIR can be found in the list and if found assign it here
		SmUserDirectory userDir = null;
		for (Object ob : userDirs) {
			String dir = (String) ob;
			if (dir.equals(siteminderService.smUserDirectory())) {
				userDir = new SmUserDirectory(siteminderService.smUserDirectory());
				result = policyApi.getUserDirectory(siteminderService.smUserDirectory(), userDir);
				if (siteminderService.debug()) {
					SmSdkUtils.printObject(userDir, result);
				}
			}
		}

		SmDmsApi dmsApi = new SmDmsApiImpl(apiSession);
		SmDmsDirectoryContext dirContext = new SmDmsDirectoryContext();
		result = dmsApi.getDirectoryContext(userDir, new SmDmsConfig(), dirContext);

		if (!result.isSuccess()) {
			logger.error("LegacySMCreateForgeRockUser::connectToThePolicyServer > getDirectoryContext STATUS_NOK");
			agent.unInit();
			return null;
		} else {
			logger.info("LegacySMCreateForgeRockUser::connectToThePolicyServer > getDirectoryContext STATUS_OK");
		}

		SmDmsDirectory dmsDirectory = dirContext.getDmsDirectory();
		SmDmsOrganization dmsOrg = dmsDirectory.newOrganization(siteminderService.smDirectoryRoot());
		String dmsSearch = "(&(objectclass=" + siteminderService.smUserSearchClass() + ") ("
				+ siteminderService.smUserSearchAttr() + "=" + userName + "))";

		SmDmsSearch search = new SmDmsSearch(dmsSearch, siteminderService.smDirectoryRoot());

		// Define search parameters - no need to have them configurable since we always
		// look for a single result

		// Number of levels to search.
		search.setScope(2);
		// Initialize forward search start
		search.setNextItem(0);
		// Max number of items to display
		search.setMaxItems(1);
		// Initialize back search start
		search.setPreviousItem(0);
		// Max items in the result set
		search.setMaxResults(1);

		result = dmsOrg.search(search, 1);
		Vector<Object> vsearch = search.getResults();
		vsearch.remove(0);
		SmDmsObject dmsObj;
		if (vsearch.size() == 1) {
			dmsObj = (SmDmsObject) vsearch.get(0);
			logger.info("LegacySMCreateForgeRockUser::connectToThePolicyServer > found object: {}", dmsObj);
			if (siteminderService.debug()) {
				SmSdkUtils.printObject(dmsObj, result);
			}
			agent.unInit();
			return SmSdkUtils.getUserAttributes(dmsObj, config.migrationAttributesMap(), siteminderService.debug());
		}
		return null;
	}
}