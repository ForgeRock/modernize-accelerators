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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.secrets.Secrets;
import org.forgerock.openam.services.SiteminderService;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

public class LegacySMValidateTokenTest {

	@Mock
	LegacySMValidateToken.LegacyFRConfig smConfig;

	@Mock
	Secrets secrets;

	@Mock
	AnnotatedServiceRegistry serviceRegistry;

	@Mock
	Realm realm;

	private static final int VALID_CONFIG = 0;
	private static final int INVALID_CONFIG = 1;
	private static final int INVALID_AGENT_CONFIG = 2;
	private static final String FALSE_OUTCOME = "false";
	private static final String LEGACY_COOKIE_NAME = "SMSESSION";
	private final JsonValue sharedState = JsonValue.json(ImmutableMap.of(USERNAME, "kp-test", REALM, "/"));

	@BeforeMethod
	private void setup() throws SMSException, SSOException {
		initMocks(this);
		given(serviceRegistry.getRealmSingleton(SiteminderService.class, realm))
				.willReturn(generateConfigs().get(VALID_CONFIG));
	}

	@Test
	public void testInvalidConfigurationAgent4x() throws NodeProcessException, SMSException, SSOException {
		given(serviceRegistry.getRealmSingleton(SiteminderService.class, realm))
				.willReturn(generateConfigs().get(INVALID_CONFIG));
		LegacySMValidateToken node = new LegacySMValidateToken(smConfig, realm, secrets, serviceRegistry);
		try {
			node.process(getContextWithCookies());
		} catch (Exception e) {
			assertEquals("LegacySMValidateToken::process > Configuration is not valid for the selected agent type",
					e.getMessage());
		}

	}

	@Test
	public void testInvalidConfigurationAgent5x() throws NodeProcessException, SMSException, SSOException {
		given(serviceRegistry.getRealmSingleton(SiteminderService.class, realm))
				.willReturn(generateConfigs().get(INVALID_AGENT_CONFIG));
		LegacySMValidateToken node = new LegacySMValidateToken(smConfig, realm, secrets, serviceRegistry);
		try {
			node.process(getContextWithCookies());
		} catch (Exception e) {
			assertEquals("LegacySMValidateToken::process > Configuration is not valid for the selected agent type",
					e.getMessage());
		}

	}

	@Test
	public void shouldReturnFalseOutcomeWhenNoCookie() throws NodeProcessException {
		LegacySMValidateToken node = new LegacySMValidateToken(smConfig, realm, secrets, serviceRegistry);
		assertEquals(FALSE_OUTCOME, node.process(getContextWithoutCookies()).outcome);
	}

	@Test
	public void shouldReturnTrueAgentApiInitFailure() throws NodeProcessException {
		LegacySMValidateToken node = new LegacySMValidateToken(smConfig, realm, secrets, serviceRegistry);
		try {
			node.process(getContextWithCookies());
		} catch (Exception e) {
			assertEquals("LegacySMValidateToken::process > SM AgentAPI init failed with status: -1", e.getMessage());
		}

	}

	private List<Optional<SiteminderService>> generateConfigs() {
		SiteminderService validConfigService = new SiteminderService() {
			@Override
			public String policyServerIP() {
				return "96.67.149.166";
			}

			@Override
			public Integer accountingPort() {
				return 44441;
			}

			@Override
			public Integer authenticationPort() {
				return 44442;
			}

			@Override
			public Integer authorizationPort() {
				return 44443;
			}

			@Override
			public Integer connectionMin() {
				return 2;
			}

			@Override
			public Integer connectionMax() {
				return 20;
			}

			@Override
			public Integer connectionStep() {
				return 2;
			}

			@Override
			public Integer timeout() {
				return 60;
			}

			@Override
			public String webAgentName() {
				return "iisagent";
			}

			@Override
			public String webAgentPasswordSecretId() {
				return "iisagentsecretid";
			}

			@Override
			public Boolean is4xAgent() {
				return true;
			}

			@Override
			public String smHostFilePath() {
				return null;
			}

			@Override
			public Boolean debug() {
				return true;
			}

			@Override
			public String smAdminUser() {
				return "siteminder";
			}

			@Override
			public String smAdminPasswordSecretId() {
				return "siteminderadminsecretid";
			}

			@Override
			public String smUserDirectory() {
				return "ad";
			}

			@Override
			public String smDirectoryRoot() {
				return "dc=kapstone,dc=com";
			}

			@Override
			public String smUserSearchAttr() {
				return "samaccountname";
			}

			@Override
			public String smUserSearchClass() {
				return "user";
			}

			@Override
			public String protectedResource() {
				return "/sales";
			}

			@Override
			public String protectedResourceAction() {
				return "GET";
			}

			@Override
			public String legacyCookieDomain() {
				return "frdpcloud.com";
			}

			@Override
			public String legacyCookieName() {
				return LEGACY_COOKIE_NAME;
			}
		};

		SiteminderService wrongHostConfigService = new SiteminderService() {
			@Override
			public String policyServerIP() {
				return "10.10.10.10";
			}

			@Override
			public Integer accountingPort() {
				return 0;
			}

			@Override
			public Integer authenticationPort() {
				return 0;
			}

			@Override
			public Integer authorizationPort() {
				return 0;
			}

			@Override
			public Integer connectionMin() {
				return 0;
			}

			@Override
			public Integer connectionMax() {
				return 0;
			}

			@Override
			public Integer connectionStep() {
				return 0;
			}

			@Override
			public Integer timeout() {
				return 60;
			}

			@Override
			public String webAgentName() {
				return "iisagentFake";
			}

			@Override
			public String webAgentPasswordSecretId() {
				return "Passw0rdFake";
			}

			@Override
			public Boolean is4xAgent() {
				return true;
			}

			@Override
			public String smHostFilePath() {
				return null;
			}

			@Override
			public Boolean debug() {
				return false;
			}

			@Override
			public String smAdminUser() {
				return "siteminderFake";
			}

			@Override
			public String smAdminPasswordSecretId() {
				return "Kapstone@123Fake";
			}

			@Override
			public String smUserDirectory() {
				return "adFake";
			}

			@Override
			public String smDirectoryRoot() {
				return "dc=kapstone,dc=com";
			}

			@Override
			public String smUserSearchAttr() {
				return "samaccountnameFake";
			}

			@Override
			public String smUserSearchClass() {
				return "user";
			}

			@Override
			public String protectedResource() {
				return "";
			}

			@Override
			public String protectedResourceAction() {
				return "";
			}

			@Override
			public String legacyCookieDomain() {
				return "";
			}

			@Override
			public String legacyCookieName() {
				return "";
			}
		};

		SiteminderService invalidAgentService = new SiteminderService() {
			@Override
			public String policyServerIP() {
				return "96.67.149.166";
			}

			@Override
			public Integer accountingPort() {
				return 44441;
			}

			@Override
			public Integer authenticationPort() {
				return 44442;
			}

			@Override
			public Integer authorizationPort() {
				return 44443;
			}

			@Override
			public Integer connectionMin() {
				return 2;
			}

			@Override
			public Integer connectionMax() {
				return 20;
			}

			@Override
			public Integer connectionStep() {
				return 2;
			}

			@Override
			public Integer timeout() {
				return 60;
			}

			@Override
			public String webAgentName() {
				return "iisagent";
			}

			@Override
			public String webAgentPasswordSecretId() {
				return "iisagentsecretid";
			}

			@Override
			public Boolean is4xAgent() {
				return false;
			}

			@Override
			public String smHostFilePath() {
				return null;
			}

			@Override
			public Boolean debug() {
				return true;
			}

			@Override
			public String smAdminUser() {
				return "siteminder";
			}

			@Override
			public String smAdminPasswordSecretId() {
				return "siteminderadminsecretid";
			}

			@Override
			public String smUserDirectory() {
				return "ad";
			}

			@Override
			public String smDirectoryRoot() {
				return "dc=kapstone,dc=com";
			}

			@Override
			public String smUserSearchAttr() {
				return "samaccountname";
			}

			@Override
			public String smUserSearchClass() {
				return "user";
			}

			@Override
			public String protectedResource() {
				return "/sales";
			}

			@Override
			public String protectedResourceAction() {
				return "GET";
			}

			@Override
			public String legacyCookieDomain() {
				return "frdpcloud.com";
			}

			@Override
			public String legacyCookieName() {
				return LEGACY_COOKIE_NAME;
			}
		};

		return List.of(Optional.of(validConfigService), Optional.of(wrongHostConfigService),
				Optional.of(invalidAgentService));
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
