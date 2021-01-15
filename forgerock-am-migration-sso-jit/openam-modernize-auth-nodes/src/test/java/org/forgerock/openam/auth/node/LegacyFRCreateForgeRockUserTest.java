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

import static org.forgerock.openam.auth.node.api.SharedStateConstants.OBJECT_ATTRIBUTES;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.PASSWORD;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.LEGACY_COOKIE_SHARED_STATE_PARAM;
import static org.forgerock.openam.modernize.utils.NodeConstants.USER_NAME;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.ArrayList;
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
import org.forgerock.openam.services.LegacyFRService;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

public class LegacyFRCreateForgeRockUserTest {

	LegacyFRCreateForgeRockUser.LegacyFRCreateForgeRockUserConfig config = new LegacyFRCreateForgeRockUser.LegacyFRCreateForgeRockUserConfig() {
		@Override
		public Map<String, String> migrationAttributesMap() {
			Map<String, String> map = new HashMap<>();
			map.put("cn", "cn");
			map.put("sn", "sn");
			map.put("givenName", "givenName");
			return map;
		}

		@Override
		public boolean setPasswordReset() {
			return true;
		}
	};

	@Mock
	AnnotatedServiceRegistry serviceRegistry;

	@Mock
	Realm realm;

	private static final String FALSE_OUTCOME = "false";
	private static final String TRUE_OUTCOME = "true";

	private static final int VALID_CONFIG = 0;
	private static final int INVALID_CONFIG = 1;

	private static final String GOOD_LEGACY_ENV_URL = "http://localhost:8080/openam/json/realms/root/realms/legacy/users/";
	private static final String WRONG_LEGACY_ENV_URL = "http://wronghost:8080/openam/json/realms/root/realms/legacy/users/";
	private static final String USER = "demo";

	private JsonValue sharedState = JsonValue.json(JsonValue.object(JsonValue.field(USERNAME, USER),
			JsonValue.field(REALM, "/"), JsonValue.field(LEGACY_COOKIE_SHARED_STATE_PARAM,
					"m88oJy_dPTrSw2P37oILh2eVeiE.*AAJTSQACMDEAAlNLABxEbWpQTzJudExiVmFTQVdPYno5ZkpQUlBtbGc9AAR0eXBlAANDVFMAAlMxAAA.*")));

	private final JsonValue transientState = JsonValue
			.json(JsonValue.object(JsonValue.field(PASSWORD, "Raco12s3czz4vd5dv6@")));

	@BeforeMethod
	private void setup() throws SMSException, SSOException {
		initMocks(this);
		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs().get(VALID_CONFIG));
	}

	@Test
	public void shouldReturnFalseOutcomeWhenNoCookie() {
		LegacyFRCreateForgeRockUser node = new LegacyFRCreateForgeRockUser(realm, config, serviceRegistry);

		assertEquals(FALSE_OUTCOME, node.process(getContextNoCookies()).outcome);
	}

	@Test
	public void shouldReturnFalseOutcomeWhenWrongHost() throws SMSException, SSOException {
		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs().get(INVALID_CONFIG));

		LegacyFRCreateForgeRockUser node = new LegacyFRCreateForgeRockUser(realm, config, serviceRegistry);

		assertEquals(FALSE_OUTCOME, node.process(getValidContext()).outcome);
	}

	@Test
	public void shouldReturnFalseOutcomeWhenWrongCredentials() throws SMSException, SSOException {
		LegacyFRCreateForgeRockUser node = new LegacyFRCreateForgeRockUser(realm, config, serviceRegistry) {
			@Override
			public Response getUser(String endpoint, String legacyCookie) {
				Response response = new Response(Status.OK);
				ArrayList<String> arrayList = new ArrayList<>();
				arrayList.add(USER);
				response.setEntity(JsonValue
						.json(JsonValue.object(JsonValue.field("uid", arrayList), JsonValue.field("cn", arrayList),
								JsonValue.field("sn", arrayList), JsonValue.field("givenName", arrayList))));
				return response;
			}
		};

		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs().get(VALID_CONFIG));
		assertEquals(FALSE_OUTCOME, node.process(getContextNoPassword()).outcome);
	}

	@Test
	public void shouldReturnTrueOutcomeWhenWhenEndpointAndCredentialsAreCorrectSetPassword()
			throws SMSException, SSOException {
		LegacyFRCreateForgeRockUser node = new LegacyFRCreateForgeRockUser(realm, config, serviceRegistry) {
			@Override
			public Response getUser(String endpoint, String legacyCookie) {
				Response response = new Response(Status.OK);
				ArrayList<String> arrayList = new ArrayList<>();
				arrayList.add(USER);
				response.setEntity(JsonValue
						.json(JsonValue.object(JsonValue.field("uid", arrayList), JsonValue.field("cn", arrayList),
								JsonValue.field("sn", arrayList), JsonValue.field("givenName", arrayList))));
				return response;
			}
		};

		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs().get(VALID_CONFIG));
		assertEquals(TRUE_OUTCOME, node.process(getValidContext()).outcome);
	}

	@Test
	public void shouldReturnTrueOutcomeWhenWhenEndpointAndCredentialsAreCorrectSetPasswordObjectAttributesOnSharedState()
			throws SMSException, SSOException {
		LegacyFRCreateForgeRockUser node = new LegacyFRCreateForgeRockUser(realm, config, serviceRegistry) {
			@Override
			public Response getUser(String endpoint, String legacyCookie) {
				Response response = new Response(Status.OK);
				ArrayList<String> arrayList = new ArrayList<>();
				arrayList.add(USER);
				response.setEntity(JsonValue
						.json(JsonValue.object(JsonValue.field("uid", arrayList), JsonValue.field("cn", arrayList),
								JsonValue.field("sn", arrayList), JsonValue.field("givenName", arrayList))));
				return response;
			}
		};

		sharedState = JsonValue.json(JsonValue.object(JsonValue.field(USERNAME, USER), JsonValue.field(REALM, "/"),
				JsonValue.field(LEGACY_COOKIE_SHARED_STATE_PARAM,
						"m88oJy_dPTrSw2P37oILh2eVeiE.*AAJTSQACMDEAAlNLABxEbWpQTzJudExiVmFTQVdPYno5ZkpQUlBtbGc9AAR0eXBlAANDVFMAAlMxAAA.*"),
				JsonValue.field(OBJECT_ATTRIBUTES, JsonValue.object(JsonValue.field(USER_NAME, USER)))));

		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs().get(VALID_CONFIG));
		assertEquals(TRUE_OUTCOME, node.process(getValidContext()).outcome);
	}

	@Test
	public void shouldReturnTrueOutcomeWhenWhenEndpointAndCredentialsAreCorrectNOTSetPassword()
			throws SMSException, SSOException {
		LegacyFRCreateForgeRockUser.LegacyFRCreateForgeRockUserConfig config = new LegacyFRCreateForgeRockUser.LegacyFRCreateForgeRockUserConfig() {
			@Override
			public Map<String, String> migrationAttributesMap() {
				Map<String, String> map = new HashMap<>();
				map.put("cn", "cn");
				map.put("sn", "sn");
				map.put("givenName", "givenName");
				return map;
			}

			@Override
			public boolean setPasswordReset() {
				return false;
			}
		};

		LegacyFRCreateForgeRockUser node = new LegacyFRCreateForgeRockUser(realm, config, serviceRegistry) {
			@Override
			public Response getUser(String endpoint, String legacyCookie) {
				Response response = new Response(Status.OK);
				ArrayList<String> arrayList = new ArrayList<>();
				arrayList.add(USER);
				response.setEntity(JsonValue
						.json(JsonValue.object(JsonValue.field("uid", arrayList), JsonValue.field("cn", arrayList),
								JsonValue.field("sn", arrayList), JsonValue.field("givenName", arrayList))));
				return response;
			}
		};

		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs().get(VALID_CONFIG));
		assertEquals(TRUE_OUTCOME, node.process(getValidContext()).outcome);
	}

	@Test
	public void shouldReturnTrueOutcomeWhenWhenEndpointAndCredentialsAreCorrectNOTSetPasswordObjectAttributesOnSharedState()
			throws SMSException, SSOException {
		LegacyFRCreateForgeRockUser.LegacyFRCreateForgeRockUserConfig config = new LegacyFRCreateForgeRockUser.LegacyFRCreateForgeRockUserConfig() {
			@Override
			public Map<String, String> migrationAttributesMap() {
				Map<String, String> map = new HashMap<>();
				map.put("cn", "cn");
				map.put("sn", "sn");
				map.put("givenName", "givenName");
				return map;
			}

			@Override
			public boolean setPasswordReset() {
				return false;
			}
		};

		LegacyFRCreateForgeRockUser node = new LegacyFRCreateForgeRockUser(realm, config, serviceRegistry) {
			@Override
			public Response getUser(String endpoint, String legacyCookie) {
				Response response = new Response(Status.OK);
				ArrayList<String> arrayList = new ArrayList<>();
				arrayList.add(USER);
				response.setEntity(JsonValue
						.json(JsonValue.object(JsonValue.field("uid", arrayList), JsonValue.field("cn", arrayList),
								JsonValue.field("sn", arrayList), JsonValue.field("givenName", arrayList))));
				return response;
			}
		};

		sharedState = JsonValue.json(JsonValue.object(JsonValue.field(USERNAME, USER), JsonValue.field(REALM, "/"),
				JsonValue.field(LEGACY_COOKIE_SHARED_STATE_PARAM,
						"m88oJy_dPTrSw2P37oILh2eVeiE.*AAJTSQACMDEAAlNLABxEbWpQTzJudExiVmFTQVdPYno5ZkpQUlBtbGc9AAR0eXBlAANDVFMAAlMxAAA.*"),
				JsonValue.field(OBJECT_ATTRIBUTES, JsonValue.object(JsonValue.field(USER_NAME, USER)))));

		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs().get(VALID_CONFIG));
		assertEquals(TRUE_OUTCOME, node.process(getValidContext()).outcome);
	}

	@Test
	public void shouldReturnTrueOutcomeWhenWhenEndpointAndCredentialsAreCorrectSetPasswordUserAttributesNULL()
			throws SMSException, SSOException {
		LegacyFRCreateForgeRockUser.LegacyFRCreateForgeRockUserConfig config = new LegacyFRCreateForgeRockUser.LegacyFRCreateForgeRockUserConfig() {
			@Override
			public Map<String, String> migrationAttributesMap() {
				Map<String, String> map = new HashMap<>();
				return map;
			}

			@Override
			public boolean setPasswordReset() {
				return true;
			}
		};

		LegacyFRCreateForgeRockUser node = new LegacyFRCreateForgeRockUser(realm, config, serviceRegistry) {
			@Override
			public Response getUser(String endpoint, String legacyCookie) {
				Response response = new Response(Status.OK);
				ArrayList<String> arrayList = new ArrayList<>();
				arrayList.add(USER);
				response.setEntity(JsonValue
						.json(JsonValue.object(JsonValue.field("uid", arrayList), JsonValue.field("cn", arrayList),
								JsonValue.field("sn", arrayList), JsonValue.field("givenName", arrayList))));
				return response;
			}
		};

		given(serviceRegistry.getRealmSingleton(LegacyFRService.class, realm))
				.willReturn(generateConfigs().get(VALID_CONFIG));
		assertEquals(TRUE_OUTCOME, node.process(getValidContext()).outcome);
	}

	private List<Optional<LegacyFRService>> generateConfigs() {
		LegacyFRService validConfigService = new LegacyFRService() {
			@Override
			public String legacyEnvURL() {
				return GOOD_LEGACY_ENV_URL;
			}

			@Override
			public String legacyLoginUri() {
				return "";
			}

			@Override
			public String legacyCookieName() {
				return "";
			}

			@Override
			public String checkLegacyTokenUri() {
				return "";
			}
		};

		LegacyFRService invalidConfigService = new LegacyFRService() {
			@Override
			public String legacyEnvURL() {
				return WRONG_LEGACY_ENV_URL;
			}

			@Override
			public String legacyLoginUri() {
				return "";
			}

			@Override
			public String legacyCookieName() {
				return "";
			}

			@Override
			public String checkLegacyTokenUri() {
				return "";
			}
		};
		return List.of(Optional.of(validConfigService), Optional.of(invalidConfigService));
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
		JsonValue sharedStateNoCookie = JsonValue.json(ImmutableMap.of(USERNAME, "demo", REALM, "/"));

		return new TreeContext(sharedStateNoCookie, transientState, new ExternalRequestContext.Builder().build(),
				Collections.emptyList(), Optional.empty());
	}
}
