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
package org.forgerock.openam.modernize.legacy;

import java.io.Serializable;
import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oracle.security.am.asdk.AccessClient;
import oracle.security.am.asdk.AccessException;
import oracle.security.am.asdk.AuthenticationScheme;
import oracle.security.am.asdk.ResourceRequest;
import oracle.security.am.asdk.UserSession;

public class ORAAccessClient implements Serializable {

	private static final long serialVersionUID = 648742305178339393L;
	private static volatile ORAAccessClient accessClientInstance;
	private Logger LOGGER = LoggerFactory.getLogger(ORAAccessClient.class);

	private AccessClient ac;
	private ResourceRequest rrq;

	private ORAAccessClient() {
		if (accessClientInstance != null) {
			throw new RuntimeException("Use getInstance() method to get the single instance of this class.");
		}
	}

	public static ORAAccessClient getInstance() {
		if (accessClientInstance == null) {
			synchronized (ORAAccessClient.class) {
				if (accessClientInstance == null)
					accessClientInstance = new ORAAccessClient();
			}
		}
		return accessClientInstance;
	}

	protected ORAAccessClient readResolve() {
		return getInstance();
	}

	public String authenticateUser(String userName, String password, String protocol, String resource, String method,
			String configLocation) throws AccessException {
		initAccessClient(configLocation, protocol, resource, method);
		String responseCookie = null;
		Hashtable<String, String> creds = new Hashtable<String, String>();
		creds.put("userid", userName);
		creds.put("password", password);
		UserSession session = new UserSession(rrq, creds);
		if (session.getStatus() == UserSession.LOGGEDIN) {
			if (session.isAuthorized(rrq)) {
				LOGGER.debug("User is logged in and authorized for the" + "request at level " + session.getLevel());
				responseCookie = session.getSessionToken();
				LOGGER.debug("Session token: " + responseCookie);
			} else {
				LOGGER.debug("User is logged in but NOT authorized");
			}
		} else {
			LOGGER.debug("User is NOT logged in");
		}
		return responseCookie;
	}

	private void initAccessClient(String configLocation, String protocol, String resource, String method)
			throws AccessException {
		if (ac == null) {
			ac = AccessClient.createDefaultInstance(configLocation, AccessClient.CompatibilityMode.OAM_10G);
			LOGGER.debug("Initialising ORA access client.");
		}

		if (rrq == null) {
			rrq = new ResourceRequest(protocol, resource, method);
		}

		if (rrq.isProtected()) {
			LOGGER.debug("Resource is protected.");
			AuthenticationScheme authnScheme = new AuthenticationScheme(rrq);
			if (authnScheme.isForm()) {
				LOGGER.debug("Form Authentication Scheme.");
			} else {
				LOGGER.debug("non-Form Authentication Scheme.");
			}
		} else {
			LOGGER.debug("Resource is NOT protected.");
		}
	}
}