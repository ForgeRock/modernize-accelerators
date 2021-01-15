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
package org.forgerock.openam.auth.node.utils;

public final class HttpConstants {
	private HttpConstants() {
	}

	public static final class Methods {
		public static final String POST = "POST";
		public static final String GET = "GET";

		private Methods() {
		}
	}

	public static final class Headers {
		// Header names
		public static final String CONTENT_TYPE = "Content-Type";
		public static final String ACCEPT_API_VERSION = "Accept-API-Version";
		public static final String COOKIE = "Cookie";
		public static final String SET_COOKIE = "Set-Cookie";

		// Header values
		public static final String API_VERSION = "resource=2.0, protocol=1.0";
		public static final String LEGACY_API_VERSION = "resource=1.2";
		public static final String APPLICATION_JSON = "application/json";

		private Headers() {
		}
	}
}
