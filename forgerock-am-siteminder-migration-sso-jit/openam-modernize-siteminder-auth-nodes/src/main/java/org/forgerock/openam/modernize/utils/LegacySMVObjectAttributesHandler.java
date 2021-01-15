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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegacySMVObjectAttributesHandler {

	private static final Logger logger = LoggerFactory.getLogger(LegacySMVObjectAttributesHandler.class);
	private static LegacySMVObjectAttributesHandler instance = null;

	private LegacySMVObjectAttributesHandler() {
	}

	public static LegacySMVObjectAttributesHandler getInstance() {
		if (instance == null) {
			instance = new LegacySMVObjectAttributesHandler();
		}
		return instance;
	}

	/**
	 * Adds the user attributes on OBJECT_ATTRIBUTES on Shared State
	 *
	 * @param userName    the username
	 * @param entity      the response body
	 * @param sharedState the shared state
	 * @return the updated OBJECT_ATTRIBUTES that will be added or updated on Shared
	 *         State
	 */
	public JsonValue updateObjectAttributes(String userName, Map<String, String> entity, JsonValue sharedState) {

		JsonValue userAttributes;

		if (entity != null) {
			if (sharedState.isDefined(OBJECT_ATTRIBUTES)) {
				logger.info("LegacySMVObjectAttributesHandler::updateObjectAttributes > OBJECT_ATTRIBUTES is defined");

				JsonValue objAttributes = sharedState.get(OBJECT_ATTRIBUTES);

				for (Map.Entry<String, String> et : entity.entrySet()) {
					String key = et.getKey();
					Object val = et.getValue();
					addAttribute(objAttributes, key, val);
				}
				userAttributes = objAttributes;
			} else {
				logger.info(
						"LegacySMVObjectAttributesHandler::updateObjectAttributes > OBJECT_ATTRIBUTES is not defined");
				userAttributes = JsonValue.json(JsonValue.object());
				for (Map.Entry<String, String> et : entity.entrySet()) {
					String key = et.getKey();
					Object val = et.getValue();
					addAttribute(userAttributes, key, val);
				}
			}
			if (userAttributes != null) {
				return userAttributes;
			}
		}
		return null;
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
	public JsonValue addAttribute(JsonValue objectAttributes, String key, Object value) {
		if (objectAttributes.isDefined(key)) {
			logger.info("LegacySMVObjectAttributesHandler::addAttribute > the {} is defined", key);
			String userCn = objectAttributes.get(key).asString();
			if (!userCn.equals(value)) {
				objectAttributes.remove(key);
				objectAttributes.add(key, value);
			}
		} else {
			logger.info("LegacySMVObjectAttributesHandler::addAttribute > the {} is not defined", key);
			objectAttributes.add(key, value);
		}
		logger.info("LegacySMVObjectAttributesHandler::addAttribute > added ({}, {}) ", key, value);
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
			password = context.transientState.get(PASSWORD).asString();

			// Check if OBJECT_ATTRIBUTES on TransientState is null
			if (context.transientState.isDefined(OBJECT_ATTRIBUTES)) {
				JsonValue objectAttributes = context.transientState.get(OBJECT_ATTRIBUTES);
				if (objectAttributes.isDefined(PASSWORD)) {
					objectAttributes.remove(PASSWORD);
				}
				objectAttributes.add(PASSWORD, password);
				userAttributesTransientState = objectAttributes;
			} else {
				userAttributesTransientState = JsonValue.json(JsonValue.object(JsonValue.field(PASSWORD, password)));
			}
		}
		return userAttributesTransientState;
	}

}
