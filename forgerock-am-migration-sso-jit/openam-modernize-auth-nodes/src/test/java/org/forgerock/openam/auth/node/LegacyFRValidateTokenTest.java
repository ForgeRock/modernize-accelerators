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

import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.services.LegacyFRService;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

public class LegacyFRValidateTokenTest {

	@Mock
	LegacyFRValidateToken.LegacyFRConfig config;

	@Mock
	AnnotatedServiceRegistry serviceRegistry;

	@Mock
	Realm realm;

	private static final String FALSE_OUTCOME = "false";
	private static final String LEGACY_COOKIE_NAME = "iPlanetDirectoryPro";
	private static final String LEGACY_COOKIE_VALUE = "UUiMtSW6MGbqRvs_UeEyBF2x8Tk.*AAJTSQACMDEAAlNLABxaLzhUTlEyaUxtTnpMdzhKZnhRQkpxSmNiZWs9AAR0eXBlAANDVFMAAlMxAAA.*";
	private static final String GOOD_LEGACY_TOKEN_URI = "http://localhost:8080/openam/json/sessions?tokenId=";
	private static final String WRONG_LEGACY_TOKEN_URI = "http://wronghost:8080/openam/json/sessions?tokenId=";
	private final JsonValue sharedState = JsonValue.json(JsonValue.object((JsonValue.field(REALM, "/"))));

	@BeforeMethod
	private void setup() {
		initMocks(this);
	}

	@Test
	public void shouldReturnFalseOutcomeWhenNoCookie() throws SMSException, SSOException, NodeProcessException {

		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs(GOOD_LEGACY_TOKEN_URI));

		LegacyFRValidateToken node = new LegacyFRValidateToken(realm, config, serviceRegistry);

		assertEquals(FALSE_OUTCOME, node.process(getContextWithoutCookies()).outcome);

	}

	/**
	 * Given all the correct parameters, the test executes non-mocked method - needs
	 * up and running IDM instance, or mock
	 *
	 */
	@Test
	public void shouldReturnTrueOutcomeWhenWhenEndpointAndCredentialsAreCorrect()
			throws SMSException, SSOException, NodeProcessException {

		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs(GOOD_LEGACY_TOKEN_URI));

		LegacyFRValidateToken node = new LegacyFRValidateToken(realm, config, serviceRegistry);

		assertEquals(FALSE_OUTCOME, node.process(getContextWithCookies()).outcome);
	}

	@Test
	public void shouldReturnFalseOutcomeWhenWrongHost() throws SMSException, SSOException, NodeProcessException {
		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs(WRONG_LEGACY_TOKEN_URI));

		LegacyFRValidateToken node = new LegacyFRValidateToken(realm, config, serviceRegistry);

		assertEquals(FALSE_OUTCOME, node.process(getContextWithCookies()).outcome);
	}

	private Optional<LegacyFRService> generateConfigs(String legacyTokenUri) {
		LegacyFRService configService = new LegacyFRService() {

			@Override
			public String legacyEnvURL() {
				return "";
			}

			@Override
			public String legacyLoginUri() {
				return "";
			}

			@Override
			public String legacyCookieName() {
				return LEGACY_COOKIE_NAME;
			}

			@Override
			public String checkLegacyTokenUri() {
				return legacyTokenUri;
			}

		};

		return Optional.of(configService);
	}

	private TreeContext getContextWithCookies() {
		return new TreeContext(sharedState, JsonValue.json(""), JsonValue.json(""),
				new ExternalRequestContext.Builder().cookies(Map.of(LEGACY_COOKIE_NAME, LEGACY_COOKIE_VALUE)).build(),
				new ArrayList<>(), Optional.of("universalId"));
	}

	private TreeContext getContextWithoutCookies() {
		return new TreeContext(sharedState, JsonValue.json(""), JsonValue.json(""),
				new ExternalRequestContext.Builder().build(), new ArrayList<>(), Optional.of("universalId"));
	}
}
