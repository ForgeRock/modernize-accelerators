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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.SingleOutcomeNode;
import org.forgerock.openam.auth.node.api.TreeContext;

import com.google.inject.assistedinject.Assisted;

@Node.Metadata(outcomeProvider = SingleOutcomeNode.OutcomeProvider.class, configClass = AddAttributesToObjectAttributesNode.Config.class, tags = {
		"uncategorized" })
public class AddAttributesToObjectAttributesNode extends SingleOutcomeNode {

	/**
	 * Configuration for the node.
	 */
	public interface Config {
		/**
		 * List of attributes
		 *
		 * @return collection of localized messages
		 */
		@Attribute(order = 100)
		default Map<String, String> attributesList() {
			return Collections.emptyMap();
		}
	}

	private final Config config;

	@Inject
	public AddAttributesToObjectAttributesNode(@Assisted Config config) {
		this.config = config;
	}

	@Override
	public Action process(TreeContext context) {
		JsonValue newSharedState = context.sharedState.copy();
		JsonValue newTransientState = context.transientState.copy();

		newSharedState = putObjectAttributes(newSharedState);
		newTransientState = putObjectAttributes(newTransientState);

		return goToNext().replaceTransientState(newTransientState).replaceSharedState(newSharedState).build();
	}

	/**
	 * Adds attributes defined in config on <b>attributesList</b> to the
	 * OBJECT_ATTRIBUTES of the shared state or transient state in case it wasn't
	 * defined already
	 *
	 * @param copyState Copy the shared state or transient state
	 * @return The copied shared/transient state
	 */
	public JsonValue putObjectAttributes(JsonValue copyState) {
		// Initialize shared/transient state as map accordingly
		Map<String, Object> mapSharedState;
		if (copyState.isDefined(OBJECT_ATTRIBUTES)) {
			// If object attributes are defined, start with existing object attributes and
			// preserve them
			mapSharedState = copyState.get(OBJECT_ATTRIBUTES).asMap();
		} else {
			// If object attributes aren't defined, we'll build one with the given
			// attributes
			mapSharedState = new HashMap<>();
		}

		for (Map.Entry<String, String> entry : config.attributesList().entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();

			if (copyState.isDefined(key)) {
				mapSharedState.put(value, copyState.get(key).asString());
			}
		}

		copyState.remove(OBJECT_ATTRIBUTES);
		copyState.put(OBJECT_ATTRIBUTES, JsonValue.json(mapSharedState));
		return copyState;
	}
}
