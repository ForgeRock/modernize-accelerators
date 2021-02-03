/***************************************************************************
 *  Copyright 2019-2021 ForgeRock AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http:www.apache.org/licenses/LICENSE-2.0
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
import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
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

public class LegacyORACreateForgeRockUserTest {
	@Mock
	LegacyORACreateForgeRockUser.LegacyORAConfig oraConfig;

	@Mock
	AnnotatedServiceRegistry serviceRegistry;

	@Mock
	Realm realm;

	private static final int VALID_CONFIG = 0;

	private static final String FALSE_OUTCOME = "false";
	private static final String TRUE_OUTCOME = "true";
	private static final String LEGACY_COOKIE_NAME = "OAMAuthnCookie";
	private static final String USERNAME_VALUE = "JaneDoe";

	private final JsonValue transientState = JsonValue.json(new HashMap<>(Map.of(PASSWORD, "password")));
	private final JsonValue sharedState = JsonValue.json(new HashMap<>(Map.of(USERNAME, "oamuser", REALM, "/",
			LEGACY_COOKIE_SHARED_STATE_PARAM, "wmUipRaEyUzShwue67k.*AAJTSQACMDIAAlNLABxFeWFSeG84TDVGTE41b3hGeUZ2"
					+ "alFyT2hIWW89AAR0eXBlAANDVFMAAlMxAAIwMQ..*")));

	@BeforeMethod
	private void setup() throws SMSException, SSOException {
		initMocks(this);
		given(oraConfig.migrationAttributesMap())
				.willReturn(Map.of("givenName", "givenName", "cn", "cn", "password", "password"));
		given(oraConfig.setPasswordReset()).willReturn(false);
		given(serviceRegistry.getRealmSingleton(OracleService.class, realm))
				.willReturn(generateConfigs().get(VALID_CONFIG));
	}

	@Test
	public void shouldReturnFalseOutcomeWhenWrongHost() throws SMSException, SSOException {
		LegacyORACreateForgeRockUser node = new LegacyORACreateForgeRockUser(realm, oraConfig, serviceRegistry);

		given(serviceRegistry.getRealmSingleton(OracleService.class, realm))
				.willReturn(generateConfigs().get(VALID_CONFIG));
		assertEquals(FALSE_OUTCOME, node.process(getContextNoCookies()).outcome);
	}

	@Test
	public void shouldReturnFalseOutcomeWhenNoCookie() {
		LegacyORACreateForgeRockUser node = new LegacyORACreateForgeRockUser(realm, oraConfig, serviceRegistry);
		assertEquals(FALSE_OUTCOME, node.process(getContextNoCookies()).outcome);
	}

	@Test
	public void shouldReturnFalseOutcomeWhenWrongCredentials() {
		LegacyORACreateForgeRockUser node = new LegacyORACreateForgeRockUser(realm, oraConfig, serviceRegistry) {
			@Override
			public Response getUser(String endpoint, String legacyCookie) {
				return new Response(Status.OK);
			}
		};

		assertEquals(FALSE_OUTCOME, node.process(getContextNoPassword()).outcome);
	}

	@Test
	public void shouldReturnFalseOutcomeWhenGotUnsuccessfulResponse() {
		LegacyORACreateForgeRockUser node = new LegacyORACreateForgeRockUser(realm, oraConfig, serviceRegistry) {
			@Override
			public Response getUser(String endpoint, String legacyCookie) {
				return new Response(Status.UNAUTHORIZED);
			}
		};
		assertEquals(FALSE_OUTCOME, node.process(getValidContext()).outcome);
	}

	/**
	 * Given all the correct parameters, the test executes non-mocked method - needs
	 * up and running IDM instance, or mock
	 */
	@Test
	public void shouldReturnTrueOutcomeWhenWhenEndpointAndCredentialsAreCorrect() {
		given(oraConfig.setPasswordReset()).willReturn(true);

		LegacyORACreateForgeRockUser node = new LegacyORACreateForgeRockUser(realm, oraConfig, serviceRegistry) {
			public Response getUser(String endpoint, String legacyCookie) {
				Response response = new Response(Status.OK);

				response.setEntity(JsonValue.json(
						JsonValue.object(JsonValue.field("uid", USERNAME_VALUE), JsonValue.field("cn", USERNAME_VALUE),
								JsonValue.field("sn", USERNAME_VALUE), JsonValue.field("givenName", USERNAME_VALUE))));
				return response;
			}
		};
		assertEquals(TRUE_OUTCOME, node.process(getValidContext()).outcome);
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
		return new TreeContext(sharedState, transientState, new ExternalRequestContext.Builder().build(),
				Collections.emptyList(), Optional.empty());
	}

	private TreeContext getContextNoPassword() {
		return new TreeContext(sharedState, JsonValue.json(""), new ExternalRequestContext.Builder().build(),
				Collections.emptyList(), Optional.empty());
	}

	private TreeContext getContextNoCookies() {
		JsonValue sharedStateNoCookie = JsonValue.json(ImmutableMap.of(USERNAME, "oamuser", REALM, "/"));

		return new TreeContext(sharedStateNoCookie, transientState, new ExternalRequestContext.Builder().build(),
				Collections.emptyList(), Optional.empty());
	}
}
