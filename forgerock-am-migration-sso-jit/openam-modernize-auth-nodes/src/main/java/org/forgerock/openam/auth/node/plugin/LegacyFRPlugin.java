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

import static java.util.Arrays.asList;

import java.util.Map;

import org.forgerock.openam.auth.node.LegacyFRCreateForgeRockUser;
import org.forgerock.openam.auth.node.LegacyFRLogin;
import org.forgerock.openam.auth.node.LegacyFRMigrationStatus;
import org.forgerock.openam.auth.node.LegacyFRSetPassword;
import org.forgerock.openam.auth.node.LegacyFRValidateToken;
import org.forgerock.openam.auth.node.api.AbstractNodeAmPlugin;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.plugins.PluginException;

import com.google.common.collect.ImmutableMap;

/**
 * Plugin that defines the list of nodes that will be installed installed.
 */
public class LegacyFRPlugin extends AbstractNodeAmPlugin {

	/**
	 * The plugin version. This must be in semver (semantic version) format.
	 *
	 * @return The version of the plugin.
	 * @see <a href=
	 *      "https://www.osgi.org/wp-content/uploads/SemanticVersioning.pdf">Semantic
	 *      Versioning</a>
	 */
	@Override
	public String getPluginVersion() {
		return "0.0.0";
	}

	/**
	 * Retrieve the Map of list of node classes that the plugin is providing. The
	 * mappings returned describe which nodes have been introduced in which version
	 * of this plugin.
	 * 
	 * @return The list of node classes.
	 */
	@Override
	protected Map<String, Iterable<? extends Class<? extends Node>>> getNodesByVersion() {
		return ImmutableMap.of(getPluginVersion(), asList(LegacyFRCreateForgeRockUser.class, LegacyFRLogin.class,
				LegacyFRMigrationStatus.class, LegacyFRSetPassword.class, LegacyFRValidateToken.class));
	}

	/**
	 * This method will be called when the version returned by
	 * {@link #getPluginVersion()} is higher than the version already installed.
	 * This method will be called before the {@link #onStartup()} method.
	 *
	 * @param fromVersion The old version of the plugin that has been installed.
	 */
	@Override
	public void upgrade(String fromVersion) throws PluginException {
		pluginTools.upgradeAuthNode(LegacyFRCreateForgeRockUser.class);
		pluginTools.upgradeAuthNode(LegacyFRLogin.class);
		pluginTools.upgradeAuthNode(LegacyFRMigrationStatus.class);
		pluginTools.upgradeAuthNode(LegacyFRSetPassword.class);
		pluginTools.upgradeAuthNode(LegacyFRValidateToken.class);
	}
}