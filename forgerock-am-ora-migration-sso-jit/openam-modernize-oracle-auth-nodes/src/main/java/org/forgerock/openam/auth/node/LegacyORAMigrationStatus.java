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

import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;

import javax.inject.Inject;

import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.node.base.AbstractLegacyMigrationStatusNode;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.secrets.Secrets;
import org.forgerock.openam.secrets.SecretsProviderFacade;
import org.forgerock.secrets.NoSuchSecretException;
import org.forgerock.secrets.Purpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;

/**
 * 
 * <p>
 * This node validates if the user that accessed the tree is already migrated
 * into ForgeRock IDM.
 * </p>
 *
 */
@Node.Metadata(configClass = AbstractLegacyMigrationStatusNode.Config.class, outcomeProvider = AbstractLegacyMigrationStatusNode.OutcomeProvider.class)
public class LegacyORAMigrationStatus extends AbstractLegacyMigrationStatusNode {

	private Logger LOGGER = LoggerFactory.getLogger(LegacyORAMigrationStatus.class);
	private final AbstractLegacyMigrationStatusNode.Config config;
	private String idmPassword;

	@Inject
	public LegacyORAMigrationStatus(@Assisted LegacyORAMigrationStatus.Config config, @Assisted Realm realm,
			Secrets secrets) throws NodeProcessException {
		this.config = config;
		SecretsProviderFacade secretsProvider = secrets.getRealmSecrets(realm);
		if (secretsProvider != null) {
			try {
				this.idmPassword = secretsProvider.getNamedSecret(Purpose.PASSWORD, config.idmPasswordId())
						.getOrThrowUninterruptibly().revealAsUtf8(String::valueOf);
			} catch (NoSuchSecretException e) {
				throw new NodeProcessException("No secret " + config.idmPasswordId() + " found");
			}
		}
	}

	/**
	 * Main method called when the node is triggered.
	 */
	@Override
	public Action process(TreeContext context) throws NodeProcessException {
		LOGGER.debug("process()::Start");
		String username = context.sharedState.get(USERNAME).asString();
		LOGGER.debug("process()::Username: " + username);
		return goTo(getUserMigrationStatus(username, config.idmUserEndpoint(), config.idmAdminUser(), idmPassword))
				.build();
	}

}