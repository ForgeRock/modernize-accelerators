package org.forgerock.openam.auth.node.plugin;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;

import java.util.Map;

import org.forgerock.openam.auth.node.CheckLegacyToken;
import org.forgerock.openam.auth.node.api.AbstractNodeAmPlugin;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.plugins.PluginException;

public class CheckLegacyTokenPlugin extends AbstractNodeAmPlugin {

	@Override
	public String getPluginVersion() {
		return "1.0.1";
	}

	@Override
	protected Map<String, Iterable<? extends Class<? extends Node>>> getNodesByVersion() {
		return singletonMap(getPluginVersion(), singleton(CheckLegacyToken.class));
	}

	@Override
	public void upgrade(String fromVersion) throws PluginException {
		pluginTools.upgradeAuthNode(CheckLegacyToken.class);
	}
}