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
package org.forgerock.openam.auth.node;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.PASSWORD;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE;
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE_DOMAIN;
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE_NAME;

import java.util.UUID;

import javax.inject.Inject;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.treehook.LegacyORASessionTreeHook;
import org.forgerock.openam.modernize.legacy.ORAAccessClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

import oracle.security.am.asdk.AccessException;

@Node.Metadata(configClass = LegacyORALogin.Config.class, outcomeProvider = AbstractDecisionNode.OutcomeProvider.class)
public class LegacyORALogin extends AbstractDecisionNode {

	private static final String LEGACY_COOKIE_NAME = "OAMAuthnCookie";
	private Logger LOGGER = LoggerFactory.getLogger(LegacyORALogin.class);
	private final Config config;
	private final UUID nodeId;

	public static final String ms_resource = "msResource";
	public static final String ms_protocol = "http";
	public static final String ms_method = "GET";
	public static final String ms_domain = "msDomain";
	public static final String m_configLocation = "/config";

	public interface Config {

		@Attribute(order = 1, validators = { RequiredValueValidator.class })
		default String msResource() {
			return ms_resource;
		};

		@Attribute(order = 2, validators = { RequiredValueValidator.class })
		default String msProtocol() {
			return ms_protocol;
		};

		@Attribute(order = 3, validators = { RequiredValueValidator.class })
		default String msMethod() {
			return ms_method;
		};

		@Attribute(order = 4, validators = { RequiredValueValidator.class })
		default String legacyCookieDomain() {
			return ms_domain;
		};

		@Attribute(order = 5, validators = { RequiredValueValidator.class })
		default String legacyCookieName() {
			return LEGACY_COOKIE_NAME;
		};

		@Attribute(order = 6, validators = { RequiredValueValidator.class })
		default String msConfigLocation() {
			return m_configLocation;
		};

	}

	@Inject
	public LegacyORALogin(@Assisted LegacyORALogin.Config config, @Assisted UUID nodeId) {
		this.config = config;
		this.nodeId = nodeId;
	}

	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String username = context.sharedState.get(USERNAME).asString();
		String password = context.transientState.get(PASSWORD).asString();
		String responseCookie = getLegacyCookie(username, password);
		if (responseCookie != null) {
			LOGGER.info("process(): Successfull login in legacy system.");
			return goTo(true).putSessionProperty(SESSION_LEGACY_COOKIE, responseCookie)
					.putSessionProperty(SESSION_LEGACY_COOKIE_DOMAIN, config.legacyCookieDomain())
					.putSessionProperty(SESSION_LEGACY_COOKIE_NAME, config.legacyCookieName())
					.addSessionHook(LegacyORASessionTreeHook.class, nodeId, getClass().getSimpleName())
					.replaceSharedState(context.sharedState.put(LEGACY_COOKIE_SHARED_STATE_PARAM, responseCookie))
					.build();
		} else {
			return goTo(false).build();
		}
	}

	private String getLegacyCookie(String username, String password) {
		try {
			return ORAAccessClient.getInstance().authenticateUser(username, password, config.msProtocol(),
					config.msResource(), config.msMethod(), config.msConfigLocation());
		} catch (AccessException e) {
			e.printStackTrace();
		}
		return null;
	}
}