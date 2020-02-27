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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.forgerock.http.Client;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractLegacyLoginNode;
import org.forgerock.openam.auth.node.treehook.LegacySessionTreeHook;
import org.forgerock.util.promise.NeverThrowsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

/**
 * <p>
 * A node that authenticates the user in the legacy IAM and retrieves an SSO
 * token.
 * </p>
 */
@Node.Metadata(configClass = LegacyFRLogin.LegacyFRConfig.class, outcomeProvider = AbstractLegacyLoginNode.OutcomeProvider.class)
public class LegacyFRLogin extends AbstractLegacyLoginNode {
	private static ObjectMapper mapper = new ObjectMapper();
	private Logger LOGGER = LoggerFactory.getLogger(LegacyFRLogin.class);
	private final LegacyFRConfig config;
	private final UUID nodeId;
	private final HttpClientHandler httpClientHandler;

	/**
	 * Configuration for this node, as an extension from
	 * {@link AbstractLegacyLoginNode}
	 */
	public interface LegacyFRConfig extends AbstractLegacyLoginNode.Config {

		/**
		 * Defines the URL for the legacy IAM login service.
		 * 
		 * @return the URL for the legacy IAM login service.
		 */
		@Attribute(order = 10, validators = { RequiredValueValidator.class })
		String legacyLoginUri();

	}

	/**
	 * Creates a LegacyFRLogin node with the provided configuration
	 * 
	 * @param config the configuration for this Node.
	 * @param nodeId the ID of this node, used to bind the
	 *               {@link LegacySessionTreeHook} execution at the end of the tree.
	 */
	@Inject
	public LegacyFRLogin(@Assisted LegacyFRConfig config, @Assisted UUID nodeId, HttpClientHandler httpClientHandler) {
		this.config = config;
		this.nodeId = nodeId;
		this.httpClientHandler = httpClientHandler;
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String username = context.sharedState.get(USERNAME).asString();
		String password = context.transientState.get(PASSWORD).asString();

		String callback = null;
		try {
			callback = getCallbacks(config.legacyLoginUri());
		} catch (NeverThrowsException e) {
			throw new NodeProcessException("NeverThrowsException in async call: " + e);
		} catch (InterruptedException e) {
			throw new NodeProcessException("InterruptedException: " + e);
		} catch (IOException e1) {
			throw new NodeProcessException("IOException: " + e1);
		}
		String responseCookie = null;
		try {
			if (callback != null && !callback.isEmpty()) {
				String callbackBody = createAuthenticationCallbacks(callback, username, password);
				responseCookie = getLegacyCookie(config.legacyLoginUri(), callbackBody);
			}
		} catch (IOException | NeverThrowsException | InterruptedException e) {
			LOGGER.error("process()::IOException: " + e.getMessage());
			throw new NodeProcessException(e);
		}

		if (responseCookie != null) {
			LOGGER.info("process(): Successfull login in legacy system.");
			return goTo(true).putSessionProperty(LEGACY_COOKIE_SHARED_STATE_PARAM, responseCookie)
					.addSessionHook(LegacySessionTreeHook.class, nodeId, getClass().getSimpleName())
					.replaceSharedState(context.sharedState.put(LEGACY_COOKIE_SHARED_STATE_PARAM, responseCookie))
					.build();
		} else {
			return goTo(false).build();
		}
	}

	/**
	 * Initializes communication with the legacy IAM requesting the authentication
	 * callbacks.
	 * 
	 * @param url the URL for the legacy IAM login service.
	 * @return the authentication callbacks
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws NeverThrowsException
	 */
	private String getCallbacks(String url) throws NeverThrowsException, IOException, InterruptedException {
		Request request = new Request();
		try {
			request.setMethod("POST").setUri(url);
		} catch (URISyntaxException e) {
			LOGGER.error("getuser()::URISyntaxException: " + e);
		}
		request.getHeaders().add("Accept-API-Version", "resource=2.0, protocol=1.0");
		request.getHeaders().add("Content-Type", "application/json");
		Client client = new Client(httpClientHandler);
		return client.send(request).getOrThrow().getEntity().getString();
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
		ObjectNode callbackNode = mapper.createObjectNode();
		callbackNode = (ObjectNode) mapper.readTree(callback);
		ObjectNode nameCallback = (ObjectNode) callbackNode.get("callbacks").get(0).get("input").get(0);
		nameCallback.put("value", userId);
		ObjectNode passwordCallback = (ObjectNode) callbackNode.get("callbacks").get(1).get("input").get(0);
		passwordCallback.put("value", password);
		return callbackNode.toString();
	}

	/**
	 * 
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
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 */
	private String getLegacyCookie(String url, String jsonBody) throws NeverThrowsException, InterruptedException {
		Request request = new Request();
		try {
			request.setMethod("POST").setUri(url);
		} catch (URISyntaxException e) {
			LOGGER.error("getuser()::URISyntaxException: " + e);
		}
		request.setEntity(jsonBody);
		request.getHeaders().add("Accept-API-Version", "resource=2.0, protocol=1.0");
		request.getHeaders().add("Content-Type", "application/json");
		Client client = new Client(httpClientHandler);
		Response responseEntity = client.send(request).getOrThrow();

		Map<String, List<String>> headers = responseEntity.getHeaders().copyAsMultiMapOfStrings();
		List<String> cookiesSet = headers.get("Set-Cookie");
		String cookie = cookiesSet.stream().filter(x -> x.contains(config.legacyCookieName())).findFirst().orElse(null);
		LOGGER.error("getCookie()::Cookie: " + cookie);
		return cookie;
	}

}