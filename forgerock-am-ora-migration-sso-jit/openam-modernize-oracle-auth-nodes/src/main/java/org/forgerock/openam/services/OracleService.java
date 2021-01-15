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
public interface OracleService {

	/**
	 * Defines the name of the resource accessed. Since the requested resource type
	 * for this particular example is HTTP, it is legal to prepend a host name and
	 * port number to the resource name, as in the following example:
	 *
	 * <pre>
	 * //Example.com:80/resource/index.html
	 * </pre>
	 *
	 * @return the OAM login endpoint (resource)
	 */
	@Attribute(order = 101)
	String msResource();

	/**
	 * Defines the type of resource being requested. Example:
	 *
	 * <pre>
	 * HTTPS
	 * </pre>
	 *
	 * @return the type of resource being accessed
	 */
	@Attribute(order = 102)
	String msProtocol();

	/**
	 * Defines which is the type of operation to be performed against the resource.
	 * When the resource type is HTTP, the possible operations are GET and POST
	 *
	 * @return the type of operation to perform
	 */
	@Attribute(order = 103)
	String msMethod();

	/**
	 * Defines the domain for which the SSO token obtained from legacy OAM will be
	 * set
	 *
	 * @return the value for the OAM cookie domain
	 */
	@Attribute(order = 104)
	String legacyCookieDomain();

	/**
	 * Defines the path where the OAM ObAccessClient.xml file is configured
	 *
	 * @return the configured path
	 */
	@Attribute(order = 106)
	String msConfigLocation();

	/**
	 * Defines the URL used for retrieving the profile information from the legacy
	 * IAM
	 *
	 * @return the configured URL used for retrieving the profile information from
	 *         the legacy IAM
	 */
	@Attribute(order = 100)
	String legacyEnvURL();

	/**
	 * Defines the legacy IAM cookie name
	 *
	 * @return the configured legacy IAM cookie name
	 */
	@Attribute(order = 105)
	String legacyCookieName();

	/**
	 * Defines the name of the attribute which holds the value of the username, from
	 * the OAM user identity
	 *
	 * @return the name of the attribute which holds the value of the username
	 */
	@Attribute(order = 107)
	String namingAttribute();
}
