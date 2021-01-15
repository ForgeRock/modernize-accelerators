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

import static java.util.Collections.emptyList;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import java.util.Optional;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.ExternalRequestContext;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.util.i18n.PreferredLocales;

/**
 * Helper class to assist with creation of {@link TreeContext} objects for use
 * from unit tests.
 */
final class TreeContextFactory {

	static final String TEST_UNIVERSAL_ID = "universalId";

	private TreeContextFactory() {
	}

	/**
	 * Creates an initial {@link TreeContext} with no shared state.
	 *
	 * @return The {@link TreeContext}.
	 */
	static TreeContext emptyTreeContext() {
		JsonValue emptySharedState = json(object());
		return newTreeContext(emptySharedState);
	}

	/**
	 * Creates a {@link TreeContext} with the provided shared state.
	 *
	 * @param sharedState The shared state to add to the {@link TreeContext}.
	 * @return The {@link TreeContext}.
	 */
	static TreeContext newTreeContext(JsonValue sharedState) {
		return new TreeContext(sharedState, new ExternalRequestContext.Builder().build(), emptyList(),
				Optional.of(TEST_UNIVERSAL_ID));
	}

	/**
	 * Creates a {@link TreeContext} with the provided shared state and preferred
	 * locales.
	 *
	 * @param sharedState      The shared state to add to the {@link TreeContext}.
	 * @param preferredLocales The preferred locales.
	 * @return The {@link TreeContext}.
	 */
	static TreeContext newTreeContext(JsonValue sharedState, PreferredLocales preferredLocales) {
		return new TreeContext(sharedState, new ExternalRequestContext.Builder().locales(preferredLocales).build(),
				emptyList(), Optional.empty());
	}

}
