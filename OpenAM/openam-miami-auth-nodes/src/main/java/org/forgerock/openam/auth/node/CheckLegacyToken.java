package org.forgerock.openam.auth.node;

import java.io.IOException;

import javax.inject.Inject;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.miami.utils.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

@Node.Metadata(configClass = CheckLegacyToken.Config.class, outcomeProvider = AbstractDecisionNode.OutcomeProvider.class)
public class CheckLegacyToken extends AbstractDecisionNode {

	private static final String LEGACY_COOKIE_NAME = "rsaSso";
	private static final String SESSION_VALIDATION_ACTION = "_action=validate";
	private Logger LOGGER = LoggerFactory.getLogger(CheckLegacyToken.class);
	private final Config config;

	public interface Config {

		@Attribute(order = 1, validators = { RequiredValueValidator.class })
		default String checkLegacyTokenUri() {
			return "https://as.legacy.miami-accelerators.com/openam/json/sessions?tokenId=";
		};

		@Attribute(order = 2, validators = { RequiredValueValidator.class })
		default String legacyCookieName() {
			return LEGACY_COOKIE_NAME;
		};

	}

	@Inject
	public CheckLegacyToken(@Assisted CheckLegacyToken.Config config) {
		this.config = config;
	}

	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String legacyCookie = context.request.cookies.get(config.legacyCookieName());
		LOGGER.info("legacyCookie: " + legacyCookie);
		String uid = validateLegacySession(legacyCookie);
		LOGGER.info("legacyCookie uid: " + uid);
		if (uid != null && legacyCookie != null) {
			if (!legacyCookie.contains(config.legacyCookieName())) {
				legacyCookie = config.legacyCookieName() + "=" + legacyCookie;
			}
			return goTo(true)
					.replaceSharedState(context.sharedState.add("username", uid).add("legacyCookie", legacyCookie))
					.build();
		}
		return goTo(false).build();
	}

	private String validateLegacySession(String legacyCookie) {
		if (legacyCookie != null && legacyCookie.length() > 0) {
			MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
			headersMap.add("Accept-API-Version", "resource=1.2");
			ResponseEntity<String> responseEntity = RequestUtils.sendPostRequest(
					config.checkLegacyTokenUri() + legacyCookie + "&" + SESSION_VALIDATION_ACTION, null,
					MediaType.APPLICATION_JSON, headersMap);
			String response = responseEntity.getBody();
			ObjectMapper mapper = new ObjectMapper();
			JsonNode responseNode = null;
			try {
				responseNode = mapper.readTree(response);
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (responseNode != null) {
				if (responseNode.get("valid").asBoolean()) {
					return responseNode.get("uid").asText();
				}
			}
		}
		return null;
	}


}