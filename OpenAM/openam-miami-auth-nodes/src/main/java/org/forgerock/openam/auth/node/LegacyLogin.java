package org.forgerock.openam.auth.node;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.PASSWORD;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.treehook.LegacySessionTreeHook;
import org.forgerock.openam.miami.utils.RequestUtils;
import org.forgerock.openam.miami.utils.SimpleUserWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

@Node.Metadata(configClass = LegacyLogin.Config.class, outcomeProvider = AbstractDecisionNode.OutcomeProvider.class)
public class LegacyLogin extends AbstractDecisionNode {

	private static final String LEGACY_COOKIE_NAME = "rsaSso";
	private Logger LOGGER = LoggerFactory.getLogger(LegacyLogin.class);
	private final Config config;
	private final UUID nodeId;

	public interface Config {

		@Attribute(order = 1, validators = { RequiredValueValidator.class })
		String legacyLoginUri();

		@Attribute(order = 2, validators = { RequiredValueValidator.class })
		default String legacyCookieName() {
			return LEGACY_COOKIE_NAME;
		};

	}

	@Inject
	public LegacyLogin(@Assisted LegacyLogin.Config config, @Assisted UUID nodeId) {
		this.config = config;
		this.nodeId = nodeId;
	}

	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String username = context.sharedState.get(USERNAME).asString();
		String password = context.transientState.get(PASSWORD).asString();

		SimpleUserWrapper userInfo = new SimpleUserWrapper();
		if (username != null && password != null) {
			userInfo.setUserName(username);
			userInfo.setUserPassword(password);
		}

		String callback = getCallbacks(config.legacyLoginUri());
		String responseCookie = null;
		try {
			if (callback != null && !callback.isEmpty()) {
				String callbackBody = createAuthenticationCallbacks(callback, userInfo.getUserName(),
						userInfo.getUserPassword());
				responseCookie = getCookie(config.legacyLoginUri(), callbackBody);
			}
		} catch (IOException e) {
			LOGGER.error("process()::IOException: " + e.getMessage());
			throw new NodeProcessException(e);
		}

		if (responseCookie != null) {
			LOGGER.info("process(): Successfull login in legacy system.");
			return goTo(true).putSessionProperty("legacyCookie", responseCookie)
					.addSessionHook(LegacySessionTreeHook.class, nodeId, getClass().getSimpleName())
					.replaceSharedState(context.sharedState.put("legacyCookie", responseCookie)).build();
		} else {
			return goTo(false).build();
		}
	}

	private String getCallbacks(String url) {
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add("Accept-API-Version", "resource=2.0, protocol=1.0");
		ResponseEntity<String> responseEntity = RequestUtils.sendPostRequest(url, null, MediaType.APPLICATION_JSON,
				headersMap);
		LOGGER.debug("getCallbacks()::response.getBody(): " + responseEntity.getBody());
		return responseEntity.getBody();
	}

	private String createAuthenticationCallbacks(String callback, String userId, String password) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode callbackNode = mapper.createObjectNode();
		callbackNode = (ObjectNode) mapper.readTree(callback);
		ObjectNode nameCallback = (ObjectNode) callbackNode.get("callbacks").get(0).get("input").get(0);
		nameCallback.put("value", userId);
		ObjectNode passwordCallback = (ObjectNode) callbackNode.get("callbacks").get(1).get("input").get(0);
		passwordCallback.put("value", password);
		return callbackNode.toString();
	}

	private String getCookie(String url, String jsonBody) {
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add("Accept-API-Version", "resource=2.0, protocol=1.0");
		ResponseEntity<String> responseEntity = RequestUtils.sendPostRequest(url, jsonBody, MediaType.APPLICATION_JSON,
				headersMap);
		LOGGER.debug("getCookie()::response.getBody(): " + responseEntity.getBody());
		HttpHeaders responseHeaders = responseEntity.getHeaders();
		List<String> cookies = responseHeaders.get("Set-Cookie");
		String cookie = cookies.stream().filter(x -> x.contains(config.legacyCookieName())).findFirst().orElse(null);
		LOGGER.error("getCookie()::Cookie: " + cookie);
		return cookie;
	}

}