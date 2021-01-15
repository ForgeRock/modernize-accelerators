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
package org.forgerock.openam.auth.node;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Headers.ACCEPT_API_VERSION;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Headers.APPLICATION_JSON;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Headers.CONTENT_TYPE;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Headers.LEGACY_API_VERSION;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Methods.POST;
import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_VALIDATION_ACTION;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import javax.inject.Inject;

import org.forgerock.http.Client;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractValidateTokenNode;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.services.LegacyFRService;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.forgerock.util.promise.NeverThrowsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

/**
 * <p>
 * A node which validates if the user accessing the tree is having a legacy IAM
 * SSO Token. If the session is validated successfully, the node also saves on
 * the shared state the legacy cookie identified, and the username associated to
 * that cookie in the legacy IAM.
 * </p>
 */
@Node.Metadata(configClass = LegacyFRValidateToken.LegacyFRConfig.class, outcomeProvider = AbstractValidateTokenNode.OutcomeProvider.class, tags = {
		"migration" })
public class LegacyFRValidateToken extends AbstractValidateTokenNode {

	private final Logger logger = LoggerFactory.getLogger(LegacyFRValidateToken.class);
	private final LegacyFRConfig config;
	LegacyFRService legacyFRService;

	public interface LegacyFRConfig extends AbstractValidateTokenNode.Config {

	}

	/**
	 * Creates a LegacyFRValidateToken node with the provided configuration
	 *
	 * @param realm           the current realm of the node.
	 * @param config          the configuration for this Node.
	 * @param serviceRegistry instance of the tree's service config.
	 */
	@Inject
	public LegacyFRValidateToken(@Assisted Realm realm, @Assisted LegacyFRConfig config,
			AnnotatedServiceRegistry serviceRegistry) {
		this.config = config;
		try {
			legacyFRService = serviceRegistry.getRealmSingleton(LegacyFRService.class, realm).get();
		} catch (SSOException | SMSException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String legacyCookie = context.request.cookies.get(legacyFRService.legacyCookieName());
		String uid = null;
		logger.info("LegacyFRValidateToken::process > Start process");
		logger.info("LegacyFRValidateToken::process > legacyCookie: {}", legacyCookie);

		try {
			uid = validateLegacySession(legacyCookie);
		} catch (NeverThrowsException e) {
			logger.error("LegacyFRValidateToken::process > NeverThrowsException in async call: {0}", e);
		} catch (InterruptedException e) {
			logger.error("LegacyFRValidateToken::process > InterruptedException: {0}", e);
			Thread.currentThread().interrupt();
		} catch (UnknownHostException e) {
			logger.error("LegacyFRValidateToken::process > UnknownHostException in async call: {0}", e);
		} catch (IOException e) {
			throw new NodeProcessException("LegacyFRValidateToken::process > IOException: " + e);
		} catch (Exception e) {
			logger.error("LegacyFRValidateToken::process > Exception: {0}", e);
		}

		logger.info("LegacyFRValidateToken::process > User id from legacy cookie: {}", uid);
		if (uid != null) {
			if (!legacyCookie.contains(legacyFRService.legacyCookieName())) {
				legacyCookie = legacyFRService.legacyCookieName() + "=" + legacyCookie;
			}
			return goTo(true)
					.replaceSharedState(
							context.sharedState.add(USERNAME, uid).add(LEGACY_COOKIE_SHARED_STATE_PARAM, legacyCookie))
					.build();
		}
		return goTo(false).build();
	}

	/**
	 * Validates a legacy IAM cookie by calling the session validation endpoint.
	 *
	 * @param legacyCookie the user's legacy SSO token
	 * @return the user id if the session is valid, or <b>null</b> if the session is
	 *         invalid or something unexpected happened.
	 * @throws InterruptedException when an exception occurs
	 * @throws IOException          when an exception occurs
	 */
	private String validateLegacySession(String legacyCookie) throws InterruptedException, IOException {
		if (legacyCookie != null && legacyCookie.length() > 0) {
			try (Request request = new Request()) {
				request.setMethod(POST)
						.setUri(legacyFRService.checkLegacyTokenUri() + legacyCookie + "&" + SESSION_VALIDATION_ACTION);

				request.getHeaders().add(ACCEPT_API_VERSION, LEGACY_API_VERSION);
				request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);

				try (HttpClientHandler httpClientHandler = new HttpClientHandler()) {
					Response response = new Client(httpClientHandler).send(request).getOrThrow();
					JsonValue responseValue = JsonValue.json(response.getEntity().getJson());

					if (Boolean.TRUE.equals(responseValue.get("valid").asBoolean())) {
						return responseValue.get("uid").asString();
					}
				} catch (HttpApplicationException | UnknownHostException e) {
					logger.error("LegacyFRValidateToken::validateLegacySession > Failed. Exception: {0}", e);
				}

			} catch (URISyntaxException | UnknownHostException e) {
				logger.error("LegacyFRValidateToken::validateLegacySession > Failed. Exception: {0}", e);
			}
		}
		return null;
	}

}