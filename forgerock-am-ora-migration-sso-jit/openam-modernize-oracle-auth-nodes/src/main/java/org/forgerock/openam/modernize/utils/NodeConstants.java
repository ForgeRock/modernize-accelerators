/***************************************************************************
 *  Copyright 2019 ForgeRock AS
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
package org.forgerock.openam.modernize.utils;

/**
 * This class represents all the constants that are used by the accelerators
 * Accelerator nodes.
 *
 */
public final class NodeConstants {

	/**
	 * Private constructor.
	 */
	private NodeConstants() {
	}

	public static final String LEGACY_COOKIE_SHARED_STATE_PARAM = "legacyCookie";
	public static final String DEFAULT_IDM_USER = "idmAdminUser";
	public static final String DEFAULT_IDM_USER_PASSWORD = "idmAdminPasswordSecretId";
	public static final String OPEN_IDM_ADMIN_USERNAME_HEADER = "X-OpenIDM-Username";
	public static final String OPEN_IDM_ADMIN_PASSWORD_HEADER = "X-OpenIDM-Password";
	public static final String DEFAULT_IDM_USER_ENDPOINT = "idmUserEndpoint";

	public static final String USER_GIVEN_NAME = "givenName";
	public static final String USER_SN = "sn";
	public static final String USER_EMAIL = "mail";
	public static final String USER_NAME = "userName";
	public static final String USER_FORCE_PASSWORD_RESET = "forcePasswordReset";

	public static final String ORA_FIRST_NAME = "firstname";
	public static final String ORA_LAST_NAME = "lastname";
	public static final String ORA_MAIL = "mail";

	public static final String SESSION_LEGACY_COOKIE = "legacyCookie";
	public static final String SESSION_LEGACY_COOKIE_DOMAIN = "legacyCookieDomain";
	public static final String SESSION_LEGACY_COOKIE_NAME = "legacyCookieName";

}