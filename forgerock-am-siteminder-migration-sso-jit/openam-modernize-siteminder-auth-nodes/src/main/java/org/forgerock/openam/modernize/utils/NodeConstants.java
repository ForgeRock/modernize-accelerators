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
package org.forgerock.openam.modernize.utils;

/**
 * This class defines all the constants that are used by the accelerators
 * Accelerator nodes.
 *
 */
public final class NodeConstants {

	/**
	 * Private constructor.
	 */
	private NodeConstants() {
	}

	// Shared state & headers
	public static final String LEGACY_COOKIE_SHARED_STATE_PARAM = "legacyCookie";
	public static final String OPEN_IDM_ADMIN_USERNAME_HEADER = "X-OpenIDM-Username";
	public static final String OPEN_IDM_ADMIN_PASSWORD_HEADER = "X-OpenIDM-Password";

	// IDM attributes
	public static final String USER_FORCE_PASSWORD_RESET = "forcePasswordReset";

	// Session attributes
	public static final String SESSION_LEGACY_COOKIE = "legacyCookie";
	public static final String SESSION_LEGACY_COOKIE_DOMAIN = "legacyCookieDomain";
	public static final String SESSION_LEGACY_COOKIE_NAME = "legacyCookieName";

	// Node outcomes
	public static final String TRUE_OUTCOME_ID = "true";
	public static final String FALSE_OUTCOME_ID = "false";

	// URL constants
	public static final String QUERY_IDM_QUERY_USER_PATH = "/openidm/managed/user?_queryFilter=userName+eq+";
	public static final String QUERY_IDM_CREATE_USER_PATH = "/openidm/managed/user?_action=create";
	public static final String SESSION_VALIDATION_ACTION = "_action=validate";
	public static final String PATCH_IDM_USER_PATH = "/openidm/managed/user?_action=patch&_queryFilter=userName+eq+";

	// Siteminder params
	public static final int ACCOUNTING_PORT = 44441;
	public static final int AUTHENTICATION_PORT = 44442;
	public static final int AUTHORIZATION_PORT = 44443;
	public static final int CONNECTION_MIN = 2;
	public static final int CONNECTION_MAX = 20;
	public static final int CONNECTION_STEP = 2;
	public static final int CONNECTION_TIMEOUT = 60;
	public static final String DEFAULT_ACTION = "GET";

}
