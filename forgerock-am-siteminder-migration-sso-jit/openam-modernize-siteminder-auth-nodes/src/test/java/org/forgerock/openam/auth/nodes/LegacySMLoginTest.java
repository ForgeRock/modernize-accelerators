/***************************************************************************
 *  Copyright 2020 ForgeRock AS
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
package org.forgerock.openam.auth.nodes;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.PASSWORD;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.ACCOUNTING_PORT;
import static org.forgerock.openam.modernize.utils.NodeConstants.AUTHENTICATION_PORT;
import static org.forgerock.openam.modernize.utils.NodeConstants.AUTHORIZATION_PORT;
import static org.forgerock.openam.modernize.utils.NodeConstants.CONNECTION_MAX;
import static org.forgerock.openam.modernize.utils.NodeConstants.CONNECTION_MIN;
import static org.forgerock.openam.modernize.utils.NodeConstants.CONNECTION_STEP;
import static org.forgerock.openam.modernize.utils.NodeConstants.CONNECTION_TIMEOUT;
import static org.mockito.BDDMockito.given;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.UUID;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.LegacySMLogin;
import org.forgerock.openam.auth.node.api.ExternalRequestContext.Builder;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.secrets.Secrets;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.mockito.Mock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LegacySMLoginTest {

	@Mock
	LegacySMLogin.LegacyFRConfig config;

	@Mock
	Realm realm;

	@Mock
	Secrets secrets;

	@Mock
	private AnnotatedServiceRegistry annotatedServiceRegistry;

	private LegacySMLogin node;
	private JsonValue sharedState;

	private static final String USERNAME_VALUE = "jane.doe";
	private static final String PASSWORD_VALUE = "123456";

	@BeforeMethod
	public void setup() throws Exception {
		initMocks(this);
		sharedState = org.forgerock.json.JsonValue
				.json(object(field(USERNAME, USERNAME_VALUE), field(PASSWORD, PASSWORD_VALUE)));
		node = new LegacySMLogin(config, UUID.randomUUID(), realm, secrets);
	}

	@Test
	public void testInvalidConfigurationAgent4x() throws NodeProcessException {
		given(config.is4xAgent()).willReturn(true);
		given(config.authenticationPort()).willReturn(0);

		node = new LegacySMLogin(config, UUID.randomUUID(), realm, secrets);
		try {
			node.process(getContext(sharedState));
		} catch (Exception e) {
			assertThat(e).isInstanceOf(NodeProcessException.class)
					.hasMessage("LegacySMLogin::process: Configuration is not valid for the selected agent type");
		}

	}

	@Test
	public void testInvalidConfigurationAgent5x() throws NodeProcessException {
		given(config.is4xAgent()).willReturn(false);
		given(config.smHostFilePath()).willReturn(null);

		node = new LegacySMLogin(config, UUID.randomUUID(), realm, secrets);
		try {
			node.process(getContext(sharedState));
		} catch (Exception e) {
			assertThat(e).isInstanceOf(NodeProcessException.class)
					.hasMessage("LegacySMLogin::process: Configuration is not valid for the selected agent type");
		}

	}

	@Test
	public void testProcessFailedAgentApiInit() throws NodeProcessException {
		given(config.is4xAgent()).willReturn(true);
		given(config.policyServerIP()).willReturn("127.0.0.1");
		given(config.accountingPort()).willReturn(ACCOUNTING_PORT);
		given(config.authenticationPort()).willReturn(AUTHENTICATION_PORT);
		given(config.authorizationPort()).willReturn(AUTHORIZATION_PORT);
		given(config.connectionMin()).willReturn(CONNECTION_MIN);
		given(config.connectionMax()).willReturn(CONNECTION_MAX);
		given(config.connectionStep()).willReturn(CONNECTION_STEP);
		given(config.timeout()).willReturn(CONNECTION_TIMEOUT);
		given(config.webAgentPasswordSecretId()).willReturn("Password");

		node = new LegacySMLogin(config, UUID.randomUUID(), realm, secrets);
		try {
			node.process(getContext(sharedState));
		} catch (Exception e) {
			assertThat(e).isInstanceOf(NodeProcessException.class).hasMessage("AgentAPI init failed with status: -1");
		}

	}

	private TreeContext getContext(JsonValue sharedState) {
		return new TreeContext(sharedState, new JsonValue(""), new Builder().build(), emptyList());
	}

	@AfterMethod
	public void after() {
	}

}
