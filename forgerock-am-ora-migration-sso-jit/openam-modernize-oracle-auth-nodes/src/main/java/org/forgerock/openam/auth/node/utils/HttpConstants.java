package org.forgerock.openam.auth.node.utils;

public final class HttpConstants {
	private HttpConstants() {
	}

	public static final class Methods {
		public static final String GET = "GET";

		private Methods() {
		}
	}

	public static final class Headers {
		// Header names
		public static final String CONTENT_TYPE = "Content-Type";
		public static final String COOKIE = "Cookie";
		public static final String SET_COOKIE = "Set-Cookie";

		// Header values
		public static final String APPLICATION_JSON = "application/json";

		private Headers() {
		}
	}
}
