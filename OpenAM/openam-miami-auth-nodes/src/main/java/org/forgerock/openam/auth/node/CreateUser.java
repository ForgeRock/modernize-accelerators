package org.forgerock.openam.auth.node;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.CoreWrapper;
import org.forgerock.openam.miami.utils.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.AMIdentityRepository;
import com.sun.identity.idm.IdRepoException;
import com.sun.identity.idm.IdType;
import com.sun.identity.sm.RequiredValueValidator;

@Node.Metadata(configClass = CreateUser.Config.class, outcomeProvider = AbstractDecisionNode.OutcomeProvider.class)
public class CreateUser extends AbstractDecisionNode {

	private Logger LOGGER = LoggerFactory.getLogger(CreateUser.class);
	private final Config config;
	private final CoreWrapper coreWrapper;

	public interface Config {

		@Attribute(order = 1, validators = { RequiredValueValidator.class })
		default String legacyEnvURL() {
			return "https://as.legacy.miami-accelerators.com/openam/json/realms/root/realms/legacy/users/";
		};

		@Attribute(order = 2, validators = { RequiredValueValidator.class })
		default String idmUserEndpoint() {
			return "https://idm.dev.miami-accelerators.com/openidm/managed/user?_action=create";
		};

		@Attribute(order = 3, validators = { RequiredValueValidator.class })
		default String idmAdminUser() {
			return "openidm-admin";
		};

		@Attribute(order = 4, validators = { RequiredValueValidator.class })
		default String idmAdminPassword() {
			return "openidm-admin";
		};

		@Attribute(order = 5, validators = { RequiredValueValidator.class })
		default boolean setPasswordReset() {
			return false;
		};

	}

	@Inject
	public CreateUser(@Assisted CreateUser.Config config, CoreWrapper coreWrapper) {
		this.config = config;
		this.coreWrapper = coreWrapper;
	}

	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String legacyCookie = context.sharedState.get("legacyCookie").asString();
		String userName = context.sharedState.get("username").asString();
		String password = "";
		if (context.transientState.get("password") != null) {
			password = context.transientState.get("password").asString();
		}
		if (legacyCookie != null) {
			MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
			headersMap.add("Cookie", legacyCookie);
			ResponseEntity<String> responseEntity = RequestUtils.sendGetRequest(config.legacyEnvURL() + userName,
					MediaType.APPLICATION_JSON, headersMap);

			String firstName = null;
			String lastName = null;
			String email = null;
			if (responseEntity != null) {
				JsonNode response = null;
				try {
					ObjectMapper mapper = new ObjectMapper();
					response = mapper.readTree(responseEntity.getBody());
				} catch (IOException e) {
					LOGGER.error("successfullLogin()::IOException: " + e.getMessage());
					e.printStackTrace();
				}
				if (response != null) {

					ArrayNode firstNameArrayNode = (ArrayNode) response.get("givenName");
					if (firstNameArrayNode != null) {
						firstName = firstNameArrayNode.get(0).asText();
					}

					ArrayNode lastNameArrayNode = (ArrayNode) response.get("sn");
					if (lastNameArrayNode != null) {
						lastName = lastNameArrayNode.get(0).asText();
					}

					ArrayNode mailArrayNode = (ArrayNode) response.get("mail");
					if (mailArrayNode != null) {
						email = mailArrayNode.get(0).asText();
					}
				}
			}
			return goTo(provisionUser(userName, password, firstName, lastName, email)).build();
		}
		return goTo(false).build();
	}

	private boolean provisionUser(String userName, String password, String firstName, String lastName, String email) {
		LOGGER.error("provisionUser()::Start");
		String jsonBody = createProvisioningRequestEntity(userName, password, firstName, lastName, email);
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add("X-OpenIDM-Username", config.idmAdminUser());
		headersMap.add("X-OpenIDM-Password", config.idmAdminPassword());
		ResponseEntity<String> responseStatusCode = RequestUtils.sendPostRequest(config.idmUserEndpoint(), jsonBody,
				MediaType.APPLICATION_JSON, headersMap);
		if (responseStatusCode != null) {
			if (responseStatusCode.getStatusCodeValue() == 201) {
				LOGGER.error("provisionUser()::End - success - 201 created");
				return true;
			}
		}
		LOGGER.error("provisionUser()::End - fail");
		return false;
	}

	private String createProvisioningRequestEntity(String userName, String password, String firstName, String lastName,
			String email) {
		LOGGER.error("createProvisioningRequestEntity()::userName: " + userName);
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode node = mapper.createObjectNode();
		node.put("givenName", firstName);
		node.put("sn", lastName);
		node.put("mail", email);
		node.put("userName", userName);
		// For the case user is migrated without password
		if (password != null && password.length() > 0) {
			node.put("password", password);
		}
		if (config.setPasswordReset()) {
			node.put("forcePasswordReset", true);
		}
		String jsonString = null;
		try {
			jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
		} catch (JsonProcessingException e) {
			LOGGER.error("createProvisioningRequestEntity()::Error creating provisioning entity: " + e.getMessage());
			e.printStackTrace();
		}
		LOGGER.debug("createProvisioningRequestEntity()::entity: " + jsonString);
		return jsonString;
	}

	public AMIdentity createIdentity(String username, String password, String realm)
			throws IdRepoException, SSOException {
		AMIdentityRepository idrepo = coreWrapper.getAMIdentityRepository(coreWrapper.convertRealmPathToRealmDn(realm));
		Map<String, Set<String>> attrMap = new HashMap<String, Set<String>>();
		Set<String> passwordSet = new HashSet<String>();
		passwordSet.add(password);
		attrMap.put("userPassword", passwordSet);
		return idrepo.createIdentity(IdType.USER, username, attrMap);
	}
}