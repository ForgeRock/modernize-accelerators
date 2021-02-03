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
package org.forgerock.openam.auth.node;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.PASSWORD;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Headers.ACCEPT_API_VERSION;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Headers.API_VERSION;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Headers.APPLICATION_JSON;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Headers.CONTENT_TYPE;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Headers.SET_COOKIE;
import static org.forgerock.openam.auth.node.utils.HttpConstants.Methods.POST;
import static org.forgerock.openam.modernize.utils.NodeConstants.CALLBACKS_KEY;
import static org.forgerock.openam.modernize.utils.NodeConstants.CALLBACK_INPUT;
import static org.forgerock.openam.modernize.utils.NodeConstants.CALLBACK_VALUE;
import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE;
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_LEGACY_COOKIE_NAME;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.forgerock.http.Client;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractLegacyLoginNode;
import org.forgerock.openam.auth.node.treehook.LegacySessionTreeHook;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.services.LegacyFRService;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

/**
 * <p>
 * A node that authenticates the user in the legacy IAM and retrieves an SSO
 * token.
 * </p>
 */
@Node.Metadata(configClass = LegacyFRLogin.LegacyFRConfig.class, outcomeProvider = AbstractLegacyLoginNode.OutcomeProvider.class, tags = {
		"migration" })
public class LegacyFRLogin extends AbstractLegacyLoginNode {
	private static final ObjectMapper mapper = new ObjectMapper();
	private final Logger logger = LoggerFactory.getLogger(LegacyFRLogin.class);

	private final LegacyFRConfig config;
	private final UUID nodeId;
	LegacyFRService legacyFRService;

	/**
	 * Configuration for this node, as an extension from
	 * {@link AbstractLegacyLoginNode}
	 */
	public interface LegacyFRConfig extends AbstractLegacyLoginNode.Config {
	}

	/**
	 * Creates a LegacyFRLogin node with the provided configuration
	 *
	 * @param realm           the current realm of the node.
	 * @param config          the configuration for this Node.
	 * @param nodeId          the ID of this node, used to bind the
	 *                        {@link LegacySessionTreeHook} execution at the end of
	 *                        the tree.
	 * @param serviceRegistry instance of the tree's service config.
	 */
	@Inject
	public LegacyFRLogin(@Assisted Realm realm, @Assisted LegacyFRConfig config, @Assisted UUID nodeId,
			AnnotatedServiceRegistry serviceRegistry) {
		this.config = config;
		this.nodeId = nodeId;
		try {
			legacyFRService = serviceRegistry.getRealmSingleton(LegacyFRService.class, realm).get();
		} catch (SSOException | SMSException e) {
			logger.error("LegacyFRLogin::constructor > SSOException | SMSException: ", e);
		}
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) {
		String username = context.sharedState.get(USERNAME).asString();
		String password = context.transientState.get(PASSWORD).asString();

		String callback;
		String callbackBody;
		String responseCookie;
		try {
			callback = getCallbacks(legacyFRService.legacyLoginUri());
			callbackBody = createAuthenticationCallbacks(callback, username, password);
			responseCookie = getLegacyCookie(legacyFRService.legacyLoginUri(), callbackBody);

			if (responseCookie != null) {
				logger.info("LegacyFRLogin::process > Successful login in legacy system.");
				return goTo(true).putSessionProperty(SESSION_LEGACY_COOKIE, responseCookie)
						.putSessionProperty(SESSION_LEGACY_COOKIE_NAME, legacyFRService.legacyCookieName())
						.addSessionHook(LegacySessionTreeHook.class, nodeId, getClass().getSimpleName())
						.replaceSharedState(context.sharedState.put(LEGACY_COOKIE_SHARED_STATE_PARAM, responseCookie))
						.build();
			} else {
				return goTo(false).build();
			}
		} catch (RuntimeException e) {
			logger.error("LegacyFRLogin::process > RuntimeException: ", e);
		} catch (InterruptedException e) {
			logger.error("LegacyFRLogin::process > InterruptedException: ", e);
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			logger.error("LegacyFRLogin::process > IOException: ", e);
		}
		return goTo(false).build();
	}

	/**
	 * Initializes communication with the legacy IAM requesting the authentication
	 * callbacks.
	 *
	 * @param url the URL for the legacy IAM login service.
	 * @return the authentication callbacks
	 * @throws InterruptedException when exception occurs
	 * @throws IOException          when exception occurs
	 */
	private String getCallbacks(String url) throws IOException, InterruptedException {
		try (Request request = new Request()) {
			request.setMethod(POST).setUri(url);

			request.getHeaders().add(ACCEPT_API_VERSION, API_VERSION);
			request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);

			try (HttpClientHandler httpClientHandler = new HttpClientHandler()) {
				return new Client(httpClientHandler).send(request).getOrThrow().getEntity().getString();
			}
		} catch (URISyntaxException | HttpApplicationException e) {
			logger.error("LegacyFRLogin::getCallbacks > Failed. Exception: ", e);
		}

		return null;
	}

	/**
	 * Fills the user credentials in the authentication callbacks
	 *
	 * @param callback the authentication callbacks
	 * @param userId   the username that will be authenticated
	 * @param password the password of the user
	 * @return the authentication callbacks with credentials filled in and ready to
	 *         be sent for the authentication request.
	 * @throws IOException If there is an error reading the input callbacks
	 */
	private String createAuthenticationCallbacks(String callback, String userId, String password) throws IOException {
		ObjectNode callbackNode;
		callbackNode = (ObjectNode) mapper.readTree(callback);

		ObjectNode nameCallback = (ObjectNode) callbackNode.get(CALLBACKS_KEY).get(0).get(CALLBACK_INPUT).get(0);
		nameCallback.put(CALLBACK_VALUE, userId);

		ObjectNode passwordCallback = (ObjectNode) callbackNode.get(CALLBACKS_KEY).get(1).get(CALLBACK_INPUT).get(0);
		passwordCallback.put(CALLBACK_VALUE, password);

		return callbackNode.toString();
	}

	/**
	 * Get the SSO token from the authentication response, if the authentication
	 * request is successful.
	 *
	 * @param url      the URL for the legacy IAM login service.
	 * @param jsonBody the authentication callbacks with credentials previously
	 *                 filled in, and ready to be sent for the authentication
	 *                 request.
	 * @return <b>null</b> if no cookie could be found following the authentication
	 *         request. Otherwise, return the legacy IAM session cookie if
	 *         successful.
	 * @throws InterruptedException when exception occurs
	 */
	public String getLegacyCookie(String url, String jsonBody) throws InterruptedException {
		try (Request request = new Request()) {
			request.setMethod(POST).setUri(url);

			request.setEntity(jsonBody);
			request.getHeaders().add(ACCEPT_API_VERSION, API_VERSION);
			request.getHeaders().add(CONTENT_TYPE, APPLICATION_JSON);

			try (HttpClientHandler httpClientHandler = new HttpClientHandler()) {
				Client client = new Client(httpClientHandler);
				Response responseEntity = client.send(request).getOrThrow();

				Map<String, List<String>> headers = responseEntity.getHeaders().copyAsMultiMapOfStrings();
				List<String> cookiesSet = headers.get(SET_COOKIE);
				return cookiesSet.stream().filter(x -> x.contains(legacyFRService.legacyCookieName())).findFirst()
						.orElse(null);
			}
		} catch (URISyntaxException | IOException | HttpApplicationException e) {
			logger.error("LegacyFRLogin::getLegacyCookie > Failed. Exception: ", e);
		}

		return null;
	}
}