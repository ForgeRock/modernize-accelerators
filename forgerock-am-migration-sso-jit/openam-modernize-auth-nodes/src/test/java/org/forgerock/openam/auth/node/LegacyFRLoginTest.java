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
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.services.LegacyFRService;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

public class LegacyFRLoginTest {

	@Mock
	LegacyFRLogin.LegacyFRConfig frConfig;

	@Mock
	AnnotatedServiceRegistry serviceRegistry;

	@Mock
	Realm realm;

	private static final int VALID_CONFIG = 0;
	private static final int INVALID_HOST_CONFIG = 1;
	private static final String FALSE_OUTCOME = "false";
	private static final String LEGACY_COOKIE_NAME = "FRAuthnCookie";
	private static final String LOGIN_URI = "http://localhost:8080/openam_legacy/json/authenticate?realm=/legacy&authIndexType=service&authIndexValue=authenticate";
	private static final String WRONG_LOGIN_URI = "http://wronghost:8080";
	private static final String LEGACY_COOKIE_VALUE = "xSr2OD2zCQJxdPlHMtVO0Ax88WQ.*AAJTSQACMDEAAlNLABxRQ3MrbHlYd2ZoNXdEaENBRy8zSVBuK0pmSE09AAR0eXBlAANDVFMAAlMxAAA.*";

	private final UUID nodeId = UUID.randomUUID();

	private final JsonValue sharedState = JsonValue.json(ImmutableMap.of(USERNAME, "demo", REALM, "/legacy"));
	private final JsonValue transientState = JsonValue.json(ImmutableMap.of(PASSWORD, "Passw0rd"));

	@BeforeMethod
	private void setup() throws SMSException, SSOException {
		initMocks(this);
		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs().get(VALID_CONFIG));
	}

	@Test
	public void shouldReturnFalseOutcomeWhenWrongHost() throws Exception {
		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs().get(INVALID_HOST_CONFIG));

		LegacyFRLogin node = new LegacyFRLogin(realm, frConfig, nodeId, serviceRegistry);

		assertEquals(FALSE_OUTCOME, node.process(getValidContext()).outcome);
	}

	@Test
	public void shouldReturnTrueOutcomeWhenWhenEndpointAndCredentialsAreCorrect() {
		LegacyFRLogin node = new LegacyFRLogin(realm, frConfig, nodeId, serviceRegistry);

		assertEquals(FALSE_OUTCOME, node.process(getValidContext()).outcome);
	}

	@Test
	public void shouldReturnTrueIfResponseCookieNotNull() throws Exception {
		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs().get(VALID_CONFIG));

		LegacyFRLogin node = new LegacyFRLogin(realm, frConfig, nodeId, serviceRegistry) {
			@Override
			public String getLegacyCookie(String url, String jsonBody) {
				return LEGACY_COOKIE_VALUE;
			}
		};

		assertEquals(FALSE_OUTCOME, node.process(getValidContext()).outcome);
	}

	private List<Optional<LegacyFRService>> generateConfigs() {
		LegacyFRService configService = new LegacyFRService() {

			@Override
			public String legacyEnvURL() {
				return "";
			}

			@Override
			public String legacyLoginUri() {
				return LOGIN_URI;
			}

			@Override
			public String legacyCookieName() {
				return LEGACY_COOKIE_NAME;
			}

			@Override
			public String checkLegacyTokenUri() {
				return "";
			}

		};

		LegacyFRService wrongConfigService = new LegacyFRService() {

			@Override
			public String legacyEnvURL() {
				return "";
			}

			@Override
			public String legacyLoginUri() {
				return WRONG_LOGIN_URI;
			}

			@Override
			public String legacyCookieName() {
				return LEGACY_COOKIE_NAME;
			}

			@Override
			public String checkLegacyTokenUri() {
				return "";
			}

		};
		return List.of(Optional.of(configService), Optional.of(wrongConfigService));
	}

	private TreeContext getValidContext() {
		return new TreeContext(sharedState, transientState, JsonValue.json(""),
				new ExternalRequestContext.Builder().cookies(Map.of(LEGACY_COOKIE_NAME, "SomeCookieValue")).build(),
				new ArrayList<>(), Optional.of("universalId"));
	}

}
