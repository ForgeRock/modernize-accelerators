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
package org.forgerock.openam.auth.node.treehook;

import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE;
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE_DOMAIN;
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE_NAME;

import javax.inject.Inject;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openam.auth.node.api.TreeHook;
import org.forgerock.openam.auth.node.api.TreeHookException;
import org.forgerock.openam.auth.nodes.SetPersistentCookieNode;
import org.forgerock.openam.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.dpro.session.SessionException;

/**
 * A TreeHook for adding cookies to the HTTP response.
 */
@TreeHook.Metadata(configClass = SetPersistentCookieNode.Config.class)
public class LegacyORASessionTreeHook implements TreeHook {

	private final Session session;
	private final Response response;
	private final Request request;
	private Logger LOGGER = LoggerFactory.getLogger(LegacyORASessionTreeHook.class);

	/**
	 * The OracleSessionTreeHook constructor.
	 *
	 * @param session  the session.
	 * @param response the response.
	 */
	@Inject
	public LegacyORASessionTreeHook(@Assisted Session session, @Assisted Response response, @Assisted Request request) {
		this.session = session;
		this.response = response;
		this.request = request;
	}

	@Override
	public void accept() throws TreeHookException {
		LOGGER.debug("Creating oracle legacy cookie tree hook");
		String legacyCookie = null;
		String legacyCookieDomain = null;
		String legacyCookieName = null;
		try {
			legacyCookie = session.getProperty(SESSION_LEGACY_COOKIE);
			legacyCookieDomain = session.getProperty(SESSION_LEGACY_COOKIE_DOMAIN);
			legacyCookieName = session.getProperty(SESSION_LEGACY_COOKIE_NAME);
			LOGGER.info("accept():: " + legacyCookie);
		} catch (SessionException e) {
			e.printStackTrace();
		}
		LOGGER.debug("set-cookie: " + legacyCookieName + "=" + legacyCookie);
		response.getHeaders().add("set-cookie",
				legacyCookieName + "=" + legacyCookie + "; Path=/" + "; Domain=" + legacyCookieDomain);
	}

}
