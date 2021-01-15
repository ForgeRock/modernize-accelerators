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
package org.forgerock.openam.services;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.annotations.sm.Config;

@Config(scope = Config.Scope.REALM)
public interface SiteminderService {

	/**
	 * Siteminder Policy Server IP address.
	 *
	 * @return the configured policyServerIP
	 */
	@Attribute(order = 10)
	String policyServerIP();

	/**
	 * Siteminder Policy Server Accounting server port (0 for none). Mandatory if
	 * "Is 4x Web agent" config is activated.
	 *
	 * @return the configured accountingPort
	 */
	@Attribute(order = 20)
	Integer accountingPort();

	/**
	 * Siteminder Policy Server Authentication server port (0 for none). Mandatory
	 * if "Is 4x Web agent" config is activated.
	 *
	 * @return the configured authenticationPort
	 */
	@Attribute(order = 30)
	Integer authenticationPort();

	/**
	 * Siteminder Policy Server Authorization server port (0 for none). Mandatory if
	 * "Is 4x Web agent" config is activated.
	 *
	 * @return the configured authorizationPort
	 */
	@Attribute(order = 40)
	Integer authorizationPort();

	/**
	 * Number of initial connections. Mandatory if "Is 4x Web agent" config is
	 * activated.
	 *
	 * @return the configured connectionMin value
	 */
	@Attribute(order = 50)
	Integer connectionMin();

	/**
	 * Maximum number of connections. Mandatory if "Is 4x Web agent" config is
	 * activated.
	 *
	 * @return the configured connectionMax value
	 */
	@Attribute(order = 60)
	Integer connectionMax();

	/**
	 * Number of connections to allocate when out of connections. Mandatory if "Is
	 * 4x Web agent" config is activated.
	 *
	 * @return the configured connectionStep value
	 */
	@Attribute(order = 70)
	Integer connectionStep();

	/**
	 * Connection timeout in seconds. Mandatory if "Is 4x Web agent" config is
	 * activated.
	 *
	 * @return the configured timeout in seconds
	 */
	@Attribute(order = 80)
	Integer timeout();

	/**
	 * The agent name. This name must match the agent name provided to the Policy
	 * Server. The agent name is not case sensitive.
	 *
	 * @return the configured webAgentName
	 */
	@Attribute(order = 90)
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
	@Attribute(order = 110)
	Boolean is4xAgent();

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
	 * A debug switch used to activate additional debug information.
	 *
	 * @return configured debug value
	 */
	@Attribute(order = 130)
	Boolean debug();

	/**
	 *
	 * CreateForgeRockUser specific configs
	 *
	 */

	/**
	 * Distinguished name of the siteminder administrator
	 *
	 * @return the configured smAdminUser
	 */
	@Attribute(order = 140)
	String smAdminUser();

	/**
	 * Password of the sitemidner DMS administrator logging in
	 *
	 * @return the configured smAdminPassword
	 */
	@Attribute(order = 150)
	String smAdminPasswordSecretId();

	/**
	 * Name of the siteminder user directory
	 *
	 * @return the configured user directory
	 */
	@Attribute(order = 160)
	String smUserDirectory();

	/**
	 * The user directory root search base. For example, "dc=mycompany,dc=com"
	 *
	 * @return the configured smDirectoryRoot
	 */
	@Attribute(order = 170)
	String smDirectoryRoot();

	/**
	 * The username attribute used to search for a user, given it's username. For
	 * example, "samaccountname"
	 *
	 * @return the configured smUserSearchAttr
	 */
	@Attribute(order = 180)
	String smUserSearchAttr();

	/**
	 * The object class used to define the users -- for example, "user"
	 *
	 * @return the configured smUserSearchClass
	 */
	@Attribute(order = 190)
	String smUserSearchClass();

	/**
	 *
	 * Login specific configs
	 *
	 */

	/**
	 * The name of the resource to check -- for example, /inventory/.
	 *
	 * @return the configured protectedResource
	 */
	@Attribute(order = 200)
	String protectedResource();

	/**
	 * The action to check for the protected resource -- for example, GET.
	 *
	 * @return the configured protectedResourceAction
	 */
	@Attribute(order = 210)
	String protectedResourceAction();

	/**
	 * Defines the domain for which the SSO token obtained from legacy OAM will be
	 * set
	 *
	 * @return the value for the OAM cookie domain
	 */
	@Attribute(order = 220)
	String legacyCookieDomain();

	/**
	 * Defines the legacy IAM cookie name
	 *
	 * @return the configured legacy IAM cookie name
	 */
	@Attribute(order = 230)
	String legacyCookieName();
}
