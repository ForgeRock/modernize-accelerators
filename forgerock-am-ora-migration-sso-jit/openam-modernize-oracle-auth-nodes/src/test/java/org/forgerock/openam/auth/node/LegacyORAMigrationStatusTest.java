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

import static org.forgerock.openam.auth.node.api.SharedStateConstants.REALM;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Collections;

import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.secrets.Secrets;
import org.junit.Ignore;

import com.google.common.collect.ImmutableMap;

public class LegacyORAMigrationStatusTest {

	private static final String LOCAL_IDM = "http://localhost:8080";
	private static final String OPENIDM_ADMIN_USER = "openidm-admin";
	private static final String OPENIDM_ADMIN_SECRET = "secret";

	private final Realm realm = mock(Realm.class);
	private final Secrets secrets = mock(Secrets.class);
	private final JsonValue sharedState = JsonValue.json(ImmutableMap.of(USERNAME, "oamuser", REALM, "/"));
	private final TreeContext mockContext = new TreeContext(sharedState, new ExternalRequestContext.Builder().build(),
			Collections.emptyList());

	@Ignore
	public void shouldReturnFalseOutcomeWhenWrongHostForGetStatus() throws Exception {
		LegacyORAMigrationStatus getMigrationStatusNode = node(LOCAL_IDM, OPENIDM_ADMIN_USER, OPENIDM_ADMIN_SECRET);
		assertEquals(getMigrationStatusNode.process(mockContext).outcome, "false");
	}

	@Ignore
	public void shouldReturnFalseOutcomeWhenWrongCredentialsForGetStatus() throws Exception {
		LegacyORAMigrationStatus getMigrationStatusNode = node(LOCAL_IDM, OPENIDM_ADMIN_USER, OPENIDM_ADMIN_SECRET);
		assertEquals(getMigrationStatusNode.process(mockContext).outcome, "false");
	}

	/**
	 * Given all the correct parameters, the test executes non-mocked method - needs
	 * up and running IDM instance, or mock
	 * 
	 * @throws Exception
	 */
	@Ignore
	public void shouldReturnTrueOutcomeWhenWhenEndpointAndCredentialsAreCorrectForGetStatus() throws Exception {
		LegacyORAMigrationStatus getMigrationStatusNode = node(LOCAL_IDM, OPENIDM_ADMIN_USER, OPENIDM_ADMIN_SECRET);
		assertEquals(getMigrationStatusNode.process(mockContext).outcome, "false");
	}

	private LegacyORAMigrationStatus node(String idmEndpoint, String idmUser, String idmPasswordId)
			throws NodeProcessException, HttpApplicationException {
		return new LegacyORAMigrationStatus(new LegacyORAMigrationStatus.Config() {

			public String idmUserEndpoint() {
				return idmEndpoint;
			}

			public String idmAdminUser() {
				return idmUser;
			}

			public String idmPasswordId() {
				return idmPasswordId;
			}

		}, realm, secrets, new HttpClientHandler());
	}

}
