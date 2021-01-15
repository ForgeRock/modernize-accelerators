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
package org.forgerock.openam.modernize.legacy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

import org.forgerock.openam.services.SiteminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netegrity.sdk.apiutil.SmApiException;
import com.netegrity.sdk.apiutil.SmApiResult;
import com.netegrity.sdk.apiutil.SmApiSession;
import com.netegrity.sdk.dmsapi.SmDmsObject;
import com.netegrity.sdk.policyapi.SmObject;

import netegrity.siteminder.javaagent.AgentAPI;
import netegrity.siteminder.javaagent.Attribute;
import netegrity.siteminder.javaagent.AttributeList;
import netegrity.siteminder.javaagent.InitDef;
import netegrity.siteminder.javaagent.ServerDef;

public final class SmSdkUtils {
	private static final Logger logger = LoggerFactory.getLogger(SmSdkUtils.class);

	// Provides exclusively static methods. Cannot be instantiated
	private SmSdkUtils() {
	}

	/**
	 * Creates the Siteminder {@link ServerDef}
	 *
	 * @param siteminderService the Siteminder service containing all the
	 *                          configurations for the authentication connection
	 * @return {@link ServerDef}
	 */
	public static ServerDef createServerDefinition(SiteminderService siteminderService) {
		ServerDef serverDef = new ServerDef();
		serverDef.serverIpAddress = siteminderService.policyServerIP();
		serverDef.connectionMin = siteminderService.connectionMin();
		serverDef.connectionMax = siteminderService.connectionMax();
		serverDef.connectionStep = siteminderService.connectionStep();
		serverDef.timeout = siteminderService.timeout();
		serverDef.authenticationPort = siteminderService.authenticationPort();
		serverDef.authorizationPort = siteminderService.authorizationPort();
		serverDef.accountingPort = siteminderService.accountingPort();
		return serverDef;
	}

	/**
	 * Creates the Siteminder {@link InitDef}
	 *
	 * @param agentName        the name of the web agent
	 * @param agentSecret      the secret of the web agent
	 * @param failOver
	 * @param serverDefinition the Siteminder {@link ServerDef}
	 * @return
	 */
	public static InitDef createInitDefinition(String agentName, String agentSecret, boolean failOver,
			ServerDef serverDefinition) {
		InitDef initdef;
		initdef = new InitDef(agentName, agentSecret, failOver, serverDefinition);
		return initdef;
	}

	/**
	 * Initializes the current's instance agentAPI
	 *
	 * @param webAgentSecret agent secret in string format
	 * @return the AgentAPI initialized instance or null if initialization failed
	 */
	public static AgentAPI initConnectionAgent(SiteminderService siteminderService, String webAgentSecret) {
		// Initialize AgentAPI
		AgentAPI agentAPI = new AgentAPI();
		ServerDef serverDefinition;
		InitDef initDefinition = new InitDef();

		// Create SM server and init definitions
		if (Boolean.TRUE.equals(siteminderService.is4xAgent())) {
			serverDefinition = SmSdkUtils.createServerDefinition(siteminderService);
			initDefinition = SmSdkUtils.createInitDefinition(siteminderService.webAgentName(), webAgentSecret, false,
					serverDefinition);
		} else {
			logger.info("LegacySMValidateToken::process > Configuring AgentAPI for using a > 4.x web agent.");
			int configStatus = agentAPI.getConfig(initDefinition, siteminderService.webAgentName(),
					siteminderService.smHostFilePath());
			logger.info("LegacySMValidateToken::process > getConfig returned status: {}", configStatus);
		}

		int retCode = agentAPI.init(initDefinition);
		if (retCode == AgentAPI.SUCCESS) {
			logger.info("LegacySMValidateToken::process > SM AgentAPI init successfully");
		} else {
			logger.error("LegacySMValidateToken::process > SM AgentAPI init failed with status {}", retCode);
			agentAPI.unInit();
			agentAPI = null;
		}

		return agentAPI;
	}

	/**
	 * Displays the user attributes available after a login.
	 *
	 * @param attributeList the user attributes list
	 */
	@SuppressWarnings("rawtypes")
	public static void displayAttributes(AttributeList attributeList) {
		Enumeration enumeration = attributeList.attributes();
		if (!enumeration.hasMoreElements()) {
			logger.info("SmSdkUtils::displayAttributes > No attributes found");
		}

		while (enumeration.hasMoreElements()) {
			Attribute attr = (Attribute) enumeration.nextElement();
			String str = new String(attr.value);
			logger.info("SmSdkUtils::displayAttributes > {}={}", attr.id, str);
		}
	}

	/**
	 * Prints an object retrieved via DMS API.
	 *
	 * @param obj    the DMS object retrieved. Can be a user directory, a user, or
	 *               any other object from the directory.
	 * @param result the result of the operation that has completed and retrieved
	 *               the printed object.
	 */
	@SuppressWarnings("rawtypes")
	public static void printObject(Object obj, final SmApiResult result) {
		if (!result.isSuccess()) {
			logger.error("SmSdkUtils::printObject > STATUS_NOK");
		} else {
			logger.info("SmSdkUtils::printObject > STATUS_OK");
		}

		if (obj != null) {
			if (obj instanceof com.netegrity.sdk.policyapi.SmObject) {
				SmObject smObject = (SmObject) obj;
				Hashtable properties = new Hashtable(25);
				smObject.writeProperties(properties);
				obj = properties;
			} else if (obj instanceof com.netegrity.sdk.dmsapi.SmDmsObject) {
				SmDmsObject dmsObj = (SmDmsObject) obj;
				obj = dmsObj.getAttributes();
			}

			if (obj instanceof java.util.Hashtable) {
				Enumeration ekeys = ((Hashtable) obj).keys();
				Enumeration evalues = ((Hashtable) obj).elements();

				while (evalues.hasMoreElements()) {
					logger.info("SmSdkUtils::printObject > {}={}", ekeys.nextElement(), evalues.nextElement());
				}
			} else if (obj instanceof java.util.Vector) {
				Enumeration evalues = ((Vector) obj).elements();

				while (evalues.hasMoreElements()) {
					logger.info("SmSdkUtils::printObject > {}", evalues.nextElement());
				}
			}
		}
	}

	/**
	 * Reads the Siteminder user attributes defined as keys in the
	 * migrationAttributesMap. Creates a new map with the ForgeRock attribute names
	 * as keys and Siteminder user attribute values.
	 *
	 * @param dmsObject              the user object retrieved from DMS
	 * @param migrationAttributesMap the mapping of attributes configured in the
	 *                               CreateForgeRockUser node
	 * @param debug                  debug flag
	 * @return a map of attributes in the format expected by ForgeRock IDM
	 */
	@SuppressWarnings("rawtypes")
	public static Map<String, String> getUserAttributes(Object dmsObject, Map<String, String> migrationAttributesMap,
			boolean debug) {
		logger.info("SmSdkUtils::getUserAttributes > Start");
		Map<String, String> attributesMap = new HashMap<>();

		if (dmsObject != null) {
			if (dmsObject instanceof com.netegrity.sdk.policyapi.SmObject) {
				SmObject smObject = (SmObject) dmsObject;
				Hashtable properties = new Hashtable(25);
				smObject.writeProperties(properties);
				dmsObject = properties;
			} else if (dmsObject instanceof com.netegrity.sdk.dmsapi.SmDmsObject) {
				SmDmsObject dmsObj = (SmDmsObject) dmsObject;
				dmsObject = dmsObj.getAttributes();
			}

			if (dmsObject instanceof java.util.Hashtable) {
				Enumeration ekeys = ((Hashtable) dmsObject).keys();
				Enumeration evalues = ((Hashtable) dmsObject).elements();
				while (evalues.hasMoreElements()) {
					String key = ekeys.nextElement().toString();
					String value = evalues.nextElement().toString();
					if (migrationAttributesMap.containsKey(key)) {
						attributesMap.put(migrationAttributesMap.get(key), value);
					}
				}
			}
		}
		if (debug) {
			logger.info("SmSdkUtils::getUserAttributes > End attributesMap: {}", attributesMap);
		}
		return attributesMap;
	}

	/**
	 * Log in as a Siteminder administrator. Part of the process of retrieving a
	 * user's attributes using the Siteminder DMS API.
	 *
	 * @param apiSession      the Siteminder API session
	 * @param smAdminUser     the Siteminder administrator user
	 * @param smAdminPassword the Siteminder administrator user password
	 * @return true if login was successful, false otherwise
	 */
	public static boolean adminLogin(SmApiSession apiSession, String smAdminUser, char[] smAdminPassword) {
		try {
			InetAddress address = InetAddress.getLocalHost();
			SmApiResult result = apiSession.login(smAdminUser, String.valueOf(smAdminPassword), address, 0);
			if (!result.isSuccess()) {
				SmSdkUtils.printObject(null, result);
				return false;
			}
		} catch (UnknownHostException uhe) {
			logger.error("SmSdkUtils::adminLogin > UnknownHostException: {0}", uhe);
			return false;
		} catch (SmApiException apiException) {
			logger.error("SmSdkUtils::adminLogin > SmApiException: {0}", apiException);
			return false;
		}
		return true;
	}

	/**
	 * Verifies if the required configuration fields are not empty, for both web
	 * agent types.
	 *
	 * @param siteminderService the Siteminder service containing all the
	 *                          configurations for the authentication connection
	 * @return true if all required parameters for the given agent type are
	 *         configured, false otherwise
	 */
	public static boolean isNodeConfigurationValid(SiteminderService siteminderService) {
		if (Boolean.TRUE.equals(siteminderService.is4xAgent())) {
			logger.info(
					"SmSdkUtils::isNodeConfigurationValid() > Found valid configuration for Siteminder web agent 4.x");
			return siteminderService.accountingPort() > 0 && siteminderService.authenticationPort() > 0
					&& siteminderService.authorizationPort() > 0 && siteminderService.connectionMin() > 0
					&& siteminderService.connectionMax() > 0 && siteminderService.connectionStep() > 0
					&& siteminderService.timeout() > 0 && siteminderService.webAgentPasswordSecretId() != null;
		} else {
			logger.info(
					"SmSdkUtils::isNodeConfigurationValid() > Found valid configuration for Siteminder web agent non 4.x");
			return siteminderService.smHostFilePath() != null;
		}

	}

}
