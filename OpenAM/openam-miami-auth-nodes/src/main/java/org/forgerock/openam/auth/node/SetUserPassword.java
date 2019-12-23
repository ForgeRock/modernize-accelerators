package org.forgerock.openam.auth.node;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.PASSWORD;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.assistedinject.Assisted;
import com.sun.identity.sm.RequiredValueValidator;

@Node.Metadata(configClass = SetUserPassword.Config.class, outcomeProvider = AbstractDecisionNode.OutcomeProvider.class)
public class SetUserPassword extends AbstractDecisionNode {

	private Logger LOGGER = LoggerFactory.getLogger(SetUserPassword.class);
	private final Config config;

	public interface Config {

		@Attribute(order = 1, validators = { RequiredValueValidator.class })
		default String idmUserEndpoint() {
			return "https://idm.dev.miami-accelerators.com/openidm/managed/user?_action=patch&_queryFilter=userName+eq+";
		};

		@Attribute(order = 2, validators = { RequiredValueValidator.class })
		default String idmAdminUser() {
			return "openidm-admin";
		};

		@Attribute(order = 3, validators = { RequiredValueValidator.class })
		default String idmAdminPassword() {
			return "openidm-admin";
		};
	}

	@Inject
	public SetUserPassword(@Assisted SetUserPassword.Config config) {
		this.config = config;
	}

	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String username = context.sharedState.get(USERNAME).asString();
		String password = context.transientState.get(PASSWORD).asString();
		return goTo(setUserPassword(username, password)).build();

	}

	private boolean setUserPassword(String userName, String password) {
		LOGGER.error("setUserPassword()::Start");
		String jsonBody = createPasswordRequestEntity(password);
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add("X-OpenIDM-Username", config.idmAdminUser());
		headersMap.add("X-OpenIDM-Password", config.idmAdminPassword());
		ResponseEntity<String> responseStatusCode = RequestUtils.sendPostRequest(
				config.idmUserEndpoint() + "\"" + userName + "\"", jsonBody, MediaType.APPLICATION_JSON, headersMap);
		if (responseStatusCode != null) {
			if (responseStatusCode.getStatusCodeValue() == 200) {
				LOGGER.error("setUserPassword()::End - success - 200 OK");
				return true;
			}
		}
		LOGGER.error("setUserPassword()::End - fail");
		return false;
	}

	private String createPasswordRequestEntity(String password) {
		LOGGER.error("createPasswordRequestEntity()");
		ObjectMapper mapper = new ObjectMapper();
		ArrayNode updatesList = mapper.createArrayNode();

		ObjectNode replacePasswordNode = mapper.createObjectNode();
		replacePasswordNode.put("operation", "replace");
		replacePasswordNode.put("field", "password");
		replacePasswordNode.put("value", password);
		updatesList.add(replacePasswordNode);

		ObjectNode replacePasswordResetNode = mapper.createObjectNode();
		replacePasswordResetNode.put("operation", "replace");
		replacePasswordResetNode.put("field", "forcePasswordReset");
		replacePasswordResetNode.put("value", false);
		updatesList.add(replacePasswordResetNode);

		String jsonString = null;
		try {
			jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(updatesList);
		} catch (JsonProcessingException e) {
			LOGGER.error("createPasswordRequestEntity()::Error creating provisioning entity: " + e.getMessage());
			e.printStackTrace();
		}
		LOGGER.debug("createPasswordRequestEntity()::entity: " + jsonString);
		return jsonString;
	}
}