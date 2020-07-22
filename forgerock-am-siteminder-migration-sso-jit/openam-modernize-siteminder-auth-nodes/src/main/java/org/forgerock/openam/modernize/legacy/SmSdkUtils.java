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
package org.forgerock.openam.modernize.legacy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netegrity.sdk.apiutil.SmApiException;
import com.netegrity.sdk.apiutil.SmApiResult;
import com.netegrity.sdk.apiutil.SmApiSession;
import com.netegrity.sdk.dmsapi.SmDmsObject;
import com.netegrity.sdk.policyapi.SmObject;

import netegrity.siteminder.javaagent.Attribute;
import netegrity.siteminder.javaagent.AttributeList;
import netegrity.siteminder.javaagent.InitDef;
import netegrity.siteminder.javaagent.ServerDef;

public final class SmSdkUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(SmSdkUtils.class);

	/**
	 * 
	 * Creates the Siteminder {@link ServerDef}
	 * 
	 * @param policyServerIP     siteminder policy server IP
	 * @param authorizationPort  authorization server port (0 for none)
	 * @param connectionMin      number of initial connections
	 * @param connectionMax      maximum number of connections
	 * @param connectionStep     number of connections to allocate when out of
	 *                           connections
	 * @param timeout            connection timeout in seconds
	 * @param authenticationPort authentication server port (0 for none)
	 * @param authorizationPort  authorization server port (0 for none)
	 * @param accountingPort     accounting server port (0 for none)
	 * 
	 * @return {@link ServerDef}
	 */
	public static ServerDef createServerDefinition(String policyServerIP, int connectionMin, int connectionMax,
			int connectionStep, int timeout, int authenticationPort, int authorizationPort, int accountingPort) {
		ServerDef serverDef = new ServerDef();
		serverDef.serverIpAddress = policyServerIP;
		serverDef.connectionMin = connectionMin;
		serverDef.connectionMax = connectionMax;
		serverDef.connectionStep = connectionStep;
		serverDef.timeout = timeout;
		serverDef.authenticationPort = authenticationPort;
		serverDef.authorizationPort = authorizationPort;
		serverDef.accountingPort = accountingPort;
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
		InitDef initdef = null;
		initdef = new InitDef(agentName, agentSecret, failOver, serverDefinition);
		return initdef;
	}

	/**
	 * Displays the user attributes available after a login.
	 * 
	 * @param attributeList the user attributes list
	 */
	@SuppressWarnings("rawtypes")
	public static void displayAttributes(AttributeList attributeList) {
		Enumeration enumer = attributeList.attributes();
		if (!enumer.hasMoreElements()) {
			LOGGER.info("SmSdkUtils::displayAttributes() > No attributes found");
		}

		while (enumer.hasMoreElements()) {
			Attribute attr = (Attribute) enumer.nextElement();
			LOGGER.info("SmSdkUtils::displayAttributes() > {}={}", attr.id, new String(attr.value));
		}
	}

	/**
	 * 
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
			LOGGER.error("SmSdkUtils::printObject() > STATUS_NOK");
		} else {
			LOGGER.info("SmSdkUtils::printObject() > STATUS_OK");
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
					LOGGER.info("SmSdkUtils::printObject() > {}={}", ekeys.nextElement(), evalues.nextElement());
				}
			} else if (obj instanceof java.util.Vector) {
				Enumeration evalues = ((Vector) obj).elements();

				while (evalues.hasMoreElements()) {
					LOGGER.info("SmSdkUtils::printObject() > {}", evalues.nextElement());
				}
			}
		}
	}

	/**
	 * 
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
		LOGGER.info("SmSdkUtils::getUserAttributes() > Start");
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
			LOGGER.info("SmSdkUtils::getUserAttributes() > End attributesMap: {}", attributesMap);
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
			LOGGER.error("LegacySMCreateForgeRockUser::adminLogin() > UnknownHostException: {}", uhe);
			return false;
		} catch (SmApiException apiException) {
			LOGGER.error("LegacySMCreateForgeRockUser::adminLogin() > SmApiException: {}", apiException);
			return false;
		}
		return true;
	}

	/**
	 * 
	 * Verifies if the required configuration fields are not empty, for both web
	 * agent types.
	 * 
	 * @param is4xAgent          true if the web agent type is 4.x, false otherwise
	 * @param smHostFilePath     the path where the SmHost.conf file is located.
	 *                           Mandatory only if 4.x agent is false
	 * @param accountingPort     accounting server port (0 for none)
	 * @param authenticationPort authentication server port (0 for none)
	 * @param authorizationPort  authorization server port (0 for none)
	 * @param connectionMin      number of initial connections
	 * @param connectionMax      maximum number of connections
	 * @param connectionStep     number of connections to allocate when out of
	 *                           connections
	 * @param timeout            connection timeout in seconds
	 * @param webAgentSecret     the secret id of the AM secret that contains the
	 *                           web agent shared secret as defined in the
	 *                           SiteMinder user interface
	 * @return true if all required parameters for the given agent type are
	 *         configured, false otherwise
	 */
	public static boolean isNodeConfigurationValid(boolean is4xAgent, String smHostFilePath, int accountingPort,
			int authenticationPort, int authorizationPort, int connectionMin, int connectionMax, int connectionStep,
			int timeout, String webAgentSecret) {
		if (is4xAgent) {
			LOGGER.info(
					"SmSdkUtils::isNodeConfigurationValid() > Found valid configuration for Siteminder web agent 4.x");
			return accountingPort > 0 && authenticationPort > 0 && authorizationPort > 0 && connectionMin > 0
					&& connectionMax > 0 && connectionStep > 0 && timeout > 0 && webAgentSecret != null;
		} else {
			LOGGER.info(
					"SmSdkUtils::isNodeConfigurationValid() > Found valid configuration for Siteminder web agent non 4.x");
			return smHostFilePath != null;
		}

	}

}
