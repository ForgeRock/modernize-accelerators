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
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_EMAIL;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_GIVEN_NAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_SN;

import java.io.IOException;

import javax.inject.Inject;

import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractLegacyCreateForgeRockUserNode;
import org.forgerock.openam.auth.node.template.ForgeRockUserAttributesTemplate;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.modernize.utils.RequestUtils;
import org.forgerock.openam.secrets.Secrets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.inject.assistedinject.Assisted;

/**
 * 
 * <p>
 * This node validates creates a user in ForgeRock IDM.
 * </p>
 *
 */
@Node.Metadata(configClass = LegacyFRCreateForgeRockUser.LegacyFRCreateForgeRockUserConfig.class, outcomeProvider = AbstractDecisionNode.OutcomeProvider.class)
public class LegacyFRCreateForgeRockUser extends AbstractLegacyCreateForgeRockUserNode {

	private Logger LOGGER = LoggerFactory.getLogger(LegacyFRCreateForgeRockUser.class);

	public interface LegacyFRCreateForgeRockUserConfig extends AbstractLegacyCreateForgeRockUserNode.Config {
	}

	@Inject
	public LegacyFRCreateForgeRockUser(@Assisted LegacyFRCreateForgeRockUser.Config config, @Assisted Realm realm,
			Secrets secrets) throws NodeProcessException {
		super(config, realm, secrets);
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		String legacyCookie = context.sharedState.get(LEGACY_COOKIE_SHARED_STATE_PARAM).asString();
		String userName = context.sharedState.get(USERNAME).asString();
		String password = "";
		if (context.transientState.get(PASSWORD) != null) {
			password = context.transientState.get(PASSWORD).asString();
		}
		if (legacyCookie != null) {
			ForgeRockUserAttributesTemplate userAttributes = getUserAttributes(userName, password, legacyCookie);
			if (userAttributes != null) {
				return goTo(provisionUser(userAttributes)).build();
			}
			return goTo(provisionUser(userAttributes)).build();
		}
		return goTo(false).build();
	}

	/**
	 * 
	 * Requests the OAM user details endpoint and reads the user attributes, which
	 * then adds on {@link ForgeRockUserAttributesTemplate}
	 * 
	 * @param userName
	 * @param password
	 * @param legacyCookie
	 * @return
	 */
	private ForgeRockUserAttributesTemplate getUserAttributes(String userName, String password, String legacyCookie) {
		LOGGER.debug("getUserAttributes()::Start");
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add("Cookie", legacyCookie);

		// Call OAM user details API
		ResponseEntity<String> responseEntity = RequestUtils.sendGetRequest(config.legacyEnvURL() + userName,
				MediaType.APPLICATION_JSON, headersMap);

		// Read the user attributes from the response here. If any other attributes are
		// required, read here and extend the ForgeRockUserAttributesTemplate
		String firstName = null;
		String lastName = null;
		String email = null;

		if (responseEntity != null) {
			JsonNode response = null;
			try {
				ObjectMapper mapper = new ObjectMapper();
				response = mapper.readTree(responseEntity.getBody());
			} catch (IOException e) {
				LOGGER.error("getUserAttributes()::IOException: " + e.getMessage());
				e.printStackTrace();
			}

			if (response != null) {

				ArrayNode firstNameArrayNode = (ArrayNode) response.get(USER_GIVEN_NAME);
				if (firstNameArrayNode != null) {
					firstName = firstNameArrayNode.get(0).asText();
				}

				ArrayNode lastNameArrayNode = (ArrayNode) response.get(USER_SN);
				if (lastNameArrayNode != null) {
					lastName = lastNameArrayNode.get(0).asText();
				}

				ArrayNode mailArrayNode = (ArrayNode) response.get(USER_EMAIL);
				if (mailArrayNode != null) {
					email = mailArrayNode.get(0).asText();
				}
			}
		}

		if (userName != null && firstName != null && lastName != null && email != null) {
			ForgeRockUserAttributesTemplate userAttributes = new ForgeRockUserAttributesTemplate();
			userAttributes.setUserName(userName);
			userAttributes.setPassword(password);
			userAttributes.setFirstName(firstName);
			userAttributes.setLastName(lastName);
			userAttributes.setEmail(email);
			return userAttributes;
		}
		return null;
	}

}