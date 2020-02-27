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

import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class LegacyFRValidateTokenTest {

	private final JsonValue sharedState = JsonValue.json(ImmutableMap.of(USERNAME, "oamuser", REALM, "/"));
	private final TreeContext mockContext = new TreeContext(sharedState, new ExternalRequestContext.Builder().build(),
			Collections.emptyList());

	@Test
	public void shouldReturnFalseOutcomeWhenWrongHost() throws Exception {
		LegacyFRValidateToken passwordNode = node("iPlanetDirectoryPro", "http://wronghost:8080");
		assertEquals(passwordNode.process(mockContext).outcome, "false");
	}

	@Test
	public void shouldReturnFalseOutcomeWhenWrongCredentials() throws Exception {
		LegacyFRValidateToken passwordNode = node("iPlanetDirectoryPro", "http://wronghost:8080");
		assertEquals(passwordNode.process(mockContext).outcome, "false");
	}

	/**
	 * Given all the correct parameters, the test executes non-mocked method - needs
	 * up and running IDM instance, or mock
	 * 
	 * @throws Exception
	 */
	@Test
	public void shouldReturnTrueOutcomeWhenWhenEndpointAndCredentialsAreCorrect() throws Exception {
		LegacyFRValidateToken passwordNode = node("iPlanetDirectoryPro", "http://localhost:8080");
		assertEquals(passwordNode.process(mockContext).outcome, "true");
	}

	private LegacyFRValidateToken node(String legacyCookieName, String checkLegacyTokenUri)
			throws NodeProcessException, HttpApplicationException {
		return new LegacyFRValidateToken(new LegacyFRValidateToken.LegacyFRConfig() {

			public String legacyCookieName() {
				return legacyCookieName;
			}

			public String checkLegacyTokenUri() {
				return checkLegacyTokenUri;
			}
		}, new HttpClientHandler());
	}

}
