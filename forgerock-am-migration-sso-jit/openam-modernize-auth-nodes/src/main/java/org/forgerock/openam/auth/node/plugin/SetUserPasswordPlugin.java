/*
 * Copyright 2018-2019 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */
package org.forgerock.openam.auth.node.plugin;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;

import java.util.Map;

import org.forgerock.openam.auth.node.SetUserPassword;
import org.forgerock.openam.auth.node.api.AbstractNodeAmPlugin;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.plugins.PluginException;

/**
 * Core nodes installed by default with no engine dependencies.
 */
public class SetUserPasswordPlugin extends AbstractNodeAmPlugin {

	@Override
	public String getPluginVersion() {
		return "1.0.1";
	}

	@Override
	protected Map<String, Iterable<? extends Class<? extends Node>>> getNodesByVersion() {
		return singletonMap(getPluginVersion(), singleton(SetUserPassword.class));
	}

	@Override
	public void upgrade(String fromVersion) throws PluginException {
		pluginTools.upgradeAuthNode(SetUserPassword.class);
	}
}