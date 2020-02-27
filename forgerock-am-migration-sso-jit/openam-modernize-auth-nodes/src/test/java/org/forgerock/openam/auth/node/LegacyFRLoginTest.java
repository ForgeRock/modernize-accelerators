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

import java.util.Collections;
import java.util.UUID;

import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class LegacyFRLoginTest {
	private final UUID nodeId = UUID.randomUUID();
	private final JsonValue sharedState = JsonValue.json(ImmutableMap.of(USERNAME, "demo", REALM, "/"));
	private final TreeContext mockContext = new TreeContext(sharedState, new ExternalRequestContext.Builder().build(),
			Collections.emptyList());

	@Test
	public void shouldReturnFalseOutcomeWhenWrongHost() throws Exception {
		LegacyFRLogin loginNode = node("iPlanetDirectoryPro", "http://localhost:8080");
		assertEquals(loginNode.process(mockContext).outcome, "false");
	}

	/**
	 * Given all the correct parameters, the test executes non-mocked method - needs
	 * up and running IDM instance, or mock
	 * 
	 * @throws Exception
	 */
	@Test
	public void shouldReturnTrueOutcomeWhenWhenEndpointAndCredentialsAreCorrect() throws Exception {
		LegacyFRLogin loginNode = node("iPlanetDirectoryPro", "http://localhhost:8080");
		assertEquals(loginNode.process(mockContext).outcome, "true");
	}

	private LegacyFRLogin node(String legacyCookieName, String legacyLoginUri)
			throws NodeProcessException, HttpApplicationException {
		return new LegacyFRLogin(new LegacyFRLogin.LegacyFRConfig() {

			public String legacyCookieName() {
				return legacyCookieName;
			}

			public String legacyLoginUri() {
				return legacyLoginUri;
			}

		}, nodeId, new HttpClientHandler());
	}

}
