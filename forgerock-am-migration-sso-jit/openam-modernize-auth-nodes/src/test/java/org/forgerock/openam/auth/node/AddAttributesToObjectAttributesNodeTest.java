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
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class AddAttributesToObjectAttributesNodeTest {

	private static final int COLLECTION = 0;
	private static final int EMPTY_COLLECTION = 1;
	private static final String USER = "demo";
	private static final String USER_PASSWORD = "Password";

	private JsonValue sharedState;
	private JsonValue transientState;
	private TreeContext context;

	@BeforeMethod
	private void setup() {
	}

	@Test
	public void objectAttributesNullOnSharedStateTransientStateEmptyTestTrue() {
		// Given
		sharedState = JsonValue.json(JsonValue.object(JsonValue.field(USERNAME, USER)));
		transientState = JsonValue.json("");
		context = getContext();

		// When
		AddAttributesToObjectAttributesNode node = new AddAttributesToObjectAttributesNode(
				generateConfigs().get(COLLECTION));

		// Then
		Action result = node.process(context);

		Assert.assertEquals(result.sharedState.get(OBJECT_ATTRIBUTES).get("userName").asString(), USER);
	}

	@Test
	public void objectAttributesNullOnSharedStateAddOnEmptyTransientStateTestTrue() {
		// Given
		sharedState = JsonValue.json(JsonValue.object(JsonValue.field(USERNAME, USER)));
		transientState = JsonValue.json(JsonValue.object(JsonValue.field(PASSWORD, USER_PASSWORD)));
		context = getContext();

		// When
		AddAttributesToObjectAttributesNode node = new AddAttributesToObjectAttributesNode(
				generateConfigs().get(COLLECTION));

		// Then
		Action result = node.process(context);

		Assert.assertEquals(result.transientState.get(OBJECT_ATTRIBUTES).get(PASSWORD).asString(), USER_PASSWORD);
		Assert.assertEquals(result.sharedState.get(OBJECT_ATTRIBUTES).get("userName").asString(), USER);
	}

	@Test
	public void objectAttributesOnSharedStateAddOnEmptyTransientStateTestTrue() {
		// Given
		sharedState = JsonValue.json(JsonValue.object(JsonValue.field(USERNAME, USER),
				JsonValue.field(OBJECT_ATTRIBUTES, JsonValue.object(JsonValue.field("key", "value")))));
		transientState = JsonValue.json(JsonValue.object(JsonValue.field(PASSWORD, USER_PASSWORD)));
		context = getContext();

		// When
		AddAttributesToObjectAttributesNode node = new AddAttributesToObjectAttributesNode(
				generateConfigs().get(COLLECTION));

		// Then
		Action result = node.process(context);

		Assert.assertEquals(result.transientState.get(OBJECT_ATTRIBUTES).get(PASSWORD).asString(), USER_PASSWORD);
		Assert.assertEquals(result.sharedState.get(OBJECT_ATTRIBUTES).get("userName").asString(), USER);
	}

	@Test
	public void AttributesMapEmptyTestTrue() {
		// Given
		sharedState = JsonValue.json(JsonValue.object(JsonValue.field(USERNAME, USER),
				JsonValue.field(OBJECT_ATTRIBUTES, JsonValue.object(JsonValue.field("key", "value")))));
		transientState = JsonValue.json(JsonValue.object(JsonValue.field(PASSWORD, USER_PASSWORD)));
		context = getContext();

		// When
		AddAttributesToObjectAttributesNode node = new AddAttributesToObjectAttributesNode(
				generateConfigs().get(EMPTY_COLLECTION));

		// Then
		Action result = node.process(context);

		Assert.assertEquals(result.transientState.asMap(), transientState.asMap());
		Assert.assertEquals(result.sharedState.asMap(), sharedState.asMap());
	}

	@Test
	public void objectAttributesOnSharedStateAddOnTransientStateTestTrue() {
		// Given
		sharedState = JsonValue.json(JsonValue.object(JsonValue.field(USERNAME, USER),
				JsonValue.field(OBJECT_ATTRIBUTES, JsonValue.object(JsonValue.field("key", "value")))));
		transientState = JsonValue.json(JsonValue.object(JsonValue.field(PASSWORD, USER_PASSWORD),
				JsonValue.field(OBJECT_ATTRIBUTES, JsonValue.object(JsonValue.field("key", "value")))));
		context = getContext();

		// When
		AddAttributesToObjectAttributesNode node = new AddAttributesToObjectAttributesNode(
				generateConfigs().get(COLLECTION));

		// Then
		Action result = node.process(context);

		Assert.assertEquals(result.transientState.get(OBJECT_ATTRIBUTES).get(PASSWORD).asString(), USER_PASSWORD);
		Assert.assertEquals(result.sharedState.get(OBJECT_ATTRIBUTES).get("userName").asString(), USER);
	}

	private TreeContext getContext() {
		return new TreeContext(sharedState, transientState, new ExternalRequestContext.Builder().build(),
				Collections.emptyList(), Optional.empty());
	}

	private List<AddAttributesToObjectAttributesNode.Config> generateConfigs() {
		AddAttributesToObjectAttributesNode.Config collection = new AddAttributesToObjectAttributesNode.Config() {
			@Override
			public Map<String, String> attributesList() {
				Map<String, String> map = new HashMap<>();
				map.put("username", "userName");
				map.put(PASSWORD, PASSWORD);
				return map;
			}
		};

		AddAttributesToObjectAttributesNode.Config emptyCollection = new AddAttributesToObjectAttributesNode.Config() {
			@Override
			public Map<String, String> attributesList() {
				return Collections.emptyMap();
			}
		};
		return List.of(collection, emptyCollection);
	}
}
