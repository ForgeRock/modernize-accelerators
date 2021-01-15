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
package org.forgerock.openam.modernize.utils;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.OBJECT_ATTRIBUTES;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.PASSWORD;

import java.util.Map;

import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.utils.JsonValueBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegacyORAObjectAttributesHandler {

	private final Logger logger = LoggerFactory.getLogger(LegacyORAObjectAttributesHandler.class);

	private static LegacyORAObjectAttributesHandler instance = null;

	private LegacyORAObjectAttributesHandler() {
	}

	public static LegacyORAObjectAttributesHandler getInstance() {
		if (instance == null) {
			instance = new LegacyORAObjectAttributesHandler();
		}
		return instance;
	}

	/**
	 * Adds the user attributes on OBJECT_ATTRIBUTES on Shared State
	 *
	 * @param entity      the response body
	 * @param sharedState the shared state
	 * @param attrMap     map containing the mapping of legacy attribute names to
	 *                    IDM attribute names
	 * @return the updated OBJECT_ATTRIBUTES that will be added or updated on Shared
	 *         State
	 */
	public JsonValue updateObjectAttributes(JsonValue entity, JsonValue sharedState, Map<String, String> attrMap) {
		JsonValue userAttributes = JsonValueBuilder.jsonValue().build();
		if (entity.isNull()) {
			logger.error("LegacyORAObjectAttributesHandler::updateObjectAttributes > Null entity");
			return null;
		}

		if (sharedState.isDefined(OBJECT_ATTRIBUTES)) {
			userAttributes = sharedState.get(OBJECT_ATTRIBUTES);
		}

		for (Map.Entry<String, String> entry : attrMap.entrySet()) {
			String key = entry.getValue();
			String value = entity.get(entry.getKey()).asString();

			userAttributes = addAttribute(userAttributes, key, value);
		}

		logger.error("LegacyORAObjectAttributesHandler::updateObjectAttributes > Successfully added attributes");
		return userAttributes;
	}

	/**
	 * Adds a new attribute on OBJECT_ATTRIBUTES from Shared State
	 *
	 * @param objectAttributes the OBJECT_ATTRIBUTES from Shared State
	 * @param key              the key to be added
	 * @param value            the value to be added
	 * @return the string representing the updated OBJECT_ATTRIBUTES from Shared
	 *         State
	 */
	public JsonValue addAttribute(JsonValue objectAttributes, String key, String value) {
		if (objectAttributes.isDefined(key)) {
			String userCn = objectAttributes.get(key).asString();
			if (!userCn.equals(value)) {
				objectAttributes.remove(key);
				objectAttributes.add(key, value);
			}
		} else {
			objectAttributes.add(key, value);
		}
		return objectAttributes;
	}

	/**
	 * Put the user's password on OBJECT_ATTRIBUTES
	 *
	 * @param context the context
	 * @return the updated OBJECT_ATTRIBUTES that will be added or updated on
	 *         Transient State
	 */
	public JsonValue setPassword(TreeContext context) {
		String password;
		JsonValue userAttributesTransientState = null;
		if (context.transientState.isDefined(PASSWORD)) {
			logger.info("setPassword():: The password is defined on transient state.");
			password = context.transientState.get(PASSWORD).asString();

			// Check if OBJECT_ATTRIBUTES on TransientState is null
			if (context.transientState.isDefined(OBJECT_ATTRIBUTES)) {
				logger.info("setPassword():: The OBJECT_ATTRIBUTES is defined on transient state.");
				JsonValue objectAttributes = context.transientState.get(OBJECT_ATTRIBUTES);
				if (objectAttributes.isDefined(PASSWORD)) {
					objectAttributes.remove(PASSWORD);
				}
				objectAttributes.add(PASSWORD, password);
				logger.info("setPassword():: The password was added on OBJECT_ATTRIBUTES.");
				userAttributesTransientState = objectAttributes;
			} else {
				logger.info("setPassword():: The OBJECT_ATTRIBUTES is not defined on transient state.");
				userAttributesTransientState = JsonValue.json(JsonValue.object(JsonValue.field(PASSWORD, password)));
			}
		} else {
			logger.warn("setPassword():: The password is not defined on transient state.");
		}
		return userAttributesTransientState;
	}
}
