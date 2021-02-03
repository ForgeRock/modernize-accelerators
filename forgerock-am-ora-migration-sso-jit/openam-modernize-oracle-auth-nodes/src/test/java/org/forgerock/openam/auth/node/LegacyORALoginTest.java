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
import org.forgerock.openam.services.OracleService;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

public class LegacyORALoginTest {
	@Mock
	LegacyORALogin.LegacyORAConfig oraConfig;

	@Mock
	AnnotatedServiceRegistry serviceRegistry;

	@Mock
	Realm realm;

	private static final int VALID_CONFIG = 0;
	private static final int INVALID_HOST_CONFIG = 1;
	private static final String FALSE_OUTCOME = "false";
	private static final String LEGACY_COOKIE_NAME = "OAMAuthnCookie";
	private static final String COOKIE = "WLCL+Ht9CEKLLIEl8PgYGf6ZHcwaQrdMOGtQhA/lOTRMpj8rYViDP3yMvpIIniU4OK0MpJ1Gm0VboZCn/xbty5YUHO0/8AlgiaXm4Jyr/C+ulnb0wW+YAKDmdAu7pNwRysxgG8mYYPlx3taOO7opxs6dfXAnPJd37X7qFWXL9hgfJW9tRUZIU14wFHblh6+OqIZaW6FbpLByo3tEZkiLlhiyeBM3kjPKfwI/5zZJV0AWQSyH6nR1fThIJIFt+f+OQewQSBD40g/yhM9tF+CIYJV84USgnVMv7HEAGSyOL6LExr1MufZhf4UwKB65zWr4XK4EDcHqXaTp6NYoz53HRteOAOHQG8Frs9R14mjqXe6I9OK2QgCy3mzyRtQXYlDblBp32eM9PWNaV9tkB2HGR4Gg+dyFxZhc/MloP2tygMyLjB3HbAAa7dcexCQmkwU7iyJswSshAoWdOkctwoCOjMOXaGWwosQn50G1v1sP/L4aFlkI+KEeHL5X22HQZ6Xj";

	private final UUID nodeId = UUID.randomUUID();

	private final JsonValue sharedState = JsonValue.json(ImmutableMap.of(USERNAME, "oamuser", REALM, "/"));
	private final JsonValue transientState = JsonValue.json(ImmutableMap.of(PASSWORD, "Passw0rd123"));

	@BeforeMethod
	private void setup() throws SMSException, SSOException {
		initMocks(this);
		given(serviceRegistry.getRealmSingleton(OracleService.class, realm))
				.willReturn(generateConfigs().get(VALID_CONFIG));
	}

	@Test
	public void shouldReturnFalseOutcomeWhenNoCookie() throws SMSException, SSOException {
		LegacyORALogin node = new LegacyORALogin(realm, oraConfig, nodeId, serviceRegistry);
		assertEquals(FALSE_OUTCOME, node.process(getContextNoCookies()).outcome);
	}

	@Test
	public void shouldReturnFalseOutcomeWhenNoPassword() throws SMSException, SSOException {
		LegacyORALogin node = new LegacyORALogin(realm, oraConfig, nodeId, serviceRegistry);
		assertEquals(FALSE_OUTCOME, node.process(getContextNoCredentials()).outcome);
	}

	@Test
	public void shouldReturnFalseOutcomeWhenWrongHost() throws SMSException, SSOException {
		LegacyORALogin node = new LegacyORALogin(realm, oraConfig, nodeId, serviceRegistry);

		given(serviceRegistry.getRealmSingleton(OracleService.class, realm))
				.willReturn(generateConfigs().get(INVALID_HOST_CONFIG));
		assertEquals(FALSE_OUTCOME, node.process(getValidContext()).outcome);
	}

	/**
	 * Given all the correct parameters, the test executes non-mocked method - needs
	 * up and running IDM instance, or mock
	 */
	@Test
	public void shouldReturnTrueOutcomeWhenWhenEndpointAndCredentialsAreCorrect() throws SMSException, SSOException {
		LegacyORALogin node = new LegacyORALogin(realm, oraConfig, nodeId, serviceRegistry);
		assertEquals(FALSE_OUTCOME, node.process(getValidContext()).outcome);
	}

	private List<Optional<OracleService>> generateConfigs() {
		OracleService validConfigService = new OracleService() {
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

		OracleService wrongHostConfigService = new OracleService() {
			@Override
			public String msResource() {
				return "//some.host.amazonaws.com:14100/path/example.cgi";
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
				return "./config";
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

		return List.of(Optional.of(validConfigService), Optional.of(wrongHostConfigService));
	}

	private TreeContext getValidContext() {
		return new TreeContext(sharedState, transientState, JsonValue.json(""),
				new ExternalRequestContext.Builder().cookies(Map.of(LEGACY_COOKIE_NAME, COOKIE)).build(),
				new ArrayList<>(), Optional.of("universalId"));
	}

	private TreeContext getContextNoCookies() {
		return new TreeContext(sharedState, transientState, JsonValue.json(""),
				new ExternalRequestContext.Builder().build(), new ArrayList<>(), Optional.of("universalId"));
	}

	private TreeContext getContextNoCredentials() {
		return new TreeContext(sharedState, JsonValue.json(""), JsonValue.json(""),
				new ExternalRequestContext.Builder().build(), new ArrayList<>(), Optional.of("universalId"));
	}
}
