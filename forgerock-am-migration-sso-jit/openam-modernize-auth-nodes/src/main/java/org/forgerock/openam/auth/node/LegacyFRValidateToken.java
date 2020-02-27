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

import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;
import static org.forgerock.openam.modernize.utils.NodeConstants.SESSION_VALIDATION_ACTION;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.inject.Inject;

import org.forgerock.http.Client;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractValidateTokenNode;
import org.forgerock.util.promise.NeverThrowsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

/**
 * <p>
 * A node which validates if the user accessing the tree is having a legacy IAM
 * SSO Token. If the session is validated successfully, the node also saves on
 * the shared state the legacy cookie identified, and the username associated to
 * that cookie in the legacy IAM.
 * </p>
 */
@Node.Metadata(configClass = LegacyFRValidateToken.LegacyFRConfig.class, outcomeProvider = AbstractValidateTokenNode.OutcomeProvider.class)
public class LegacyFRValidateToken extends AbstractValidateTokenNode {

	private Logger LOGGER = LoggerFactory.getLogger(LegacyFRValidateToken.class);
	private final LegacyFRConfig config;
	private final HttpClientHandler httpClientHandler;

	public interface LegacyFRConfig extends AbstractValidateTokenNode.Config {

		/**
		 * Defines the URL for the legacy IAM session validation
		 * 
		 * @return the URL for the legacy IAM session validation
		 */
		@Attribute(order = 10, validators = { RequiredValueValidator.class })
		String checkLegacyTokenUri();

	}

	/**
	 * Creates a LegacyFRValidateToken node with the provided configuration
	 * 
	 * @param config the configuration for this Node.
	 */
	@Inject
	public LegacyFRValidateToken(@Assisted LegacyFRConfig config, HttpClientHandler httpClientHandler) {
		this.config = config;
		this.httpClientHandler = httpClientHandler;
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String legacyCookie = context.request.cookies.get(config.legacyCookieName());
		LOGGER.debug("process()::legacyCookie: " + legacyCookie);
		String uid = null;
		try {
			uid = validateLegacySession(legacyCookie);
		} catch (NeverThrowsException e) {
			throw new NodeProcessException("NeverThrowsException in async call: " + e);
		} catch (InterruptedException e) {
			throw new NodeProcessException("InterruptedException: " + e);
		} catch (IOException e) {
			throw new NodeProcessException("IOException: " + e);
		}
		LOGGER.debug("process()::User id from legacy cookie: " + uid);
		if (uid != null) {
			if (!legacyCookie.contains(config.legacyCookieName())) {
				legacyCookie = config.legacyCookieName() + "=" + legacyCookie;
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
	 * @throws InterruptedException
	 * @throws NeverThrowsException
	 * @throws IOException
	 */
	private String validateLegacySession(String legacyCookie)
			throws NeverThrowsException, InterruptedException, IOException {
		if (legacyCookie != null && legacyCookie.length() > 0) {
			Request request = new Request();
			try {
				request.setMethod("POST")
						.setUri(config.checkLegacyTokenUri() + legacyCookie + "&" + SESSION_VALIDATION_ACTION);
			} catch (URISyntaxException e) {
				LOGGER.error("getuser()::URISyntaxException: " + e);
			}
			request.getHeaders().add("Accept-API-Version", "resource=1.2");
			request.getHeaders().add("Content-Type", "application/json");
			Client client = new Client(httpClientHandler);
			Response response = client.send(request).getOrThrow();
			JsonValue responseValue = (JsonValue) response.getEntity().getJson();
			if (responseValue.get("valid").asBoolean()) {
				return responseValue.get("uid").asString();
			}
		}
		return null;
	}

}