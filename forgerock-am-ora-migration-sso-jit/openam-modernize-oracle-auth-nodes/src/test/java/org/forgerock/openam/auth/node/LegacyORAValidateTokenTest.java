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

import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.services.OracleService;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

public class LegacyORAValidateTokenTest {

	@Mock
	LegacyORAValidateToken.LegacyORAConfig oraConfig;

	@Mock
	AnnotatedServiceRegistry serviceRegistry;

	@Mock
	Realm realm;

	private static final String FALSE_OUTCOME = "false";
	private static final String LEGACY_COOKIE_NAME = "OAMAuthnCookie";
	private final JsonValue sharedState = JsonValue.json(ImmutableMap.of(USERNAME, "oamuser", REALM, "/"));

	@BeforeMethod
	private void setup() throws SMSException, SSOException {
		initMocks(this);
		given(serviceRegistry.getRealmSingleton(OracleService.class, realm)).willReturn(generateConfigs());
	}

	@Test
	public void shouldReturnFalseOutcomeWhenNoCookie() {
		LegacyORAValidateToken node = new LegacyORAValidateToken(realm, oraConfig, serviceRegistry);
		assertEquals(FALSE_OUTCOME, node.process(getContextWithoutCookies()).outcome);
	}

	/**
	 * Given all the correct parameters, the test executes non-mocked method - needs
	 * up and running IDM instance, or mock
	 */
	@Test
	public void shouldReturnTrueOutcomeWhenWhenEndpointAndCredentialsAreCorrect() {
		LegacyORAValidateToken node = new LegacyORAValidateToken(realm, oraConfig, serviceRegistry);
		assertEquals(FALSE_OUTCOME, node.process(getContextWithCookies()).outcome);
	}

	private Optional<OracleService> generateConfigs() {
		OracleService configService = new OracleService() {
			@Override
			public String msResource() {
				return "//ec2-107-23-96-14.compute-1.amazonaws.com:14100/oam/server/obrareq.cgi";
			}

			@Override
			public String msProtocol() {
				return "http";
			}

			@Override
			public String msMethod() {
				return "GET";
			}

			@Override
			public String legacyCookieDomain() {
				return "amazonaws.com";
			}

			@Override
			public String msConfigLocation() {
				return "/home/forgerock/config";
			}

			@Override
			public String legacyEnvURL() {
				return "ec2-107-23-96-14.compute-1.amazonaws.com:14100/oam/server/obrareq.cgi";
			}

			@Override
			public String legacyCookieName() {
				return LEGACY_COOKIE_NAME;
			}

			@Override
			public String namingAttribute() {
				return "cn";
			}
		};

		return Optional.of(configService);
	}

	private TreeContext getContextWithCookies() {
		return new TreeContext(sharedState, JsonValue.json(""), JsonValue.json(""),
				new ExternalRequestContext.Builder().cookies(Map.of(LEGACY_COOKIE_NAME, "SomeCookieValue")).build(),
				new ArrayList<>(), Optional.of("universalId"));
	}

	private TreeContext getContextWithoutCookies() {
		return new TreeContext(sharedState, JsonValue.json(""), JsonValue.json(""),
				new ExternalRequestContext.Builder().build(), new ArrayList<>(), Optional.of("universalId"));
	}

}
