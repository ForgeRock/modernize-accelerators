/***************************************************************************
 *  Copyright 2019-2021 ForgeRock AS
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

import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;

import javax.inject.Inject;

import org.forgerock.http.protocol.Response;
import org.forgerock.openam.auth.node.api.TreeHook;
import org.forgerock.openam.auth.nodes.SetPersistentCookieNode;
import org.forgerock.openam.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.dpro.session.SessionException;

/**
 * A TreeHook for setting in the user agent the legacy cookie obtained after a
 * successful login in a legacy system.
 */
@TreeHook.Metadata(configClass = SetPersistentCookieNode.Config.class)
public class LegacySessionTreeHook implements TreeHook {

	private final Session session;
	private final Response response;
	private final Logger logger = LoggerFactory.getLogger(LegacySessionTreeHook.class);

	/**
	 * The LegacySessionTreeHook constructor.
	 *
	 * @param session  the session.
	 * @param response the response.
	 */
	@Inject
	public LegacySessionTreeHook(@Assisted Session session, @Assisted Response response) {
		this.session = session;
		this.response = response;
	}

	/**
	 * Main method that contains the logic that needs to be executed when the
	 * session hook is called.
	 */
	@Override
	public void accept() {
		logger.info("LegacySessionTreeHook::accept > Creating legacy cookie tree hook");
		String legacyCookie = null;
		try {
			legacyCookie = session.getProperty(LEGACY_COOKIE_SHARED_STATE_PARAM);
			logger.info("LegacySessionTreeHook::accept > legacyCookie {}", legacyCookie);
		} catch (SessionException e) {
			logger.error(
					"LegacySessionTreeHook::accept > Error reading session property > LEGACY_COOKIE_SHARED_STATE_PARAM {0}",
					e);

		}
		response.getHeaders().add("set-cookie", legacyCookie);
	}

}