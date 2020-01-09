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
package org.forgerock.openam.auth.node.plugin;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;

import java.util.Map;

import org.forgerock.openam.auth.node.CheckUserMigrationStatus;
import org.forgerock.openam.auth.node.api.AbstractNodeAmPlugin;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.plugins.PluginException;

public class CheckUserMigrationStatusPlugin extends AbstractNodeAmPlugin {

	@Override
	public String getPluginVersion() {
		return "1.0.0";
	}

	@Override
	protected Map<String, Iterable<? extends Class<? extends Node>>> getNodesByVersion() {
		return singletonMap(getPluginVersion(), singleton(CheckUserMigrationStatus.class));
	}

	@Override
	public void upgrade(String fromVersion) throws PluginException {
		pluginTools.upgradeAuthNode(CheckUserMigrationStatus.class);
	}
}