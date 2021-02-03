/***************************************************************************
 *  Copyright 2019-2021 ForgeRock AS
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

import static oracle.security.am.asdk.BaseUserSession.LOGGEDIN;

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
	private static ORAAccessClient accessClientInstance;
	private final transient Logger logger = LoggerFactory.getLogger(ORAAccessClient.class);

	private transient AccessClient ac;
	private transient ResourceRequest rrq;

	private ORAAccessClient() {
		if (accessClientInstance != null) {
			throw new RuntimeException(
					"ORAAccessClient::ORAAccessClient > Use getInstance() method to get the single instance of this class.");
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

	/**
	 * Authenticate the ORA user
	 *
	 * @param userName       the username
	 * @param password       the user's password
	 * @param protocol       the protocol
	 * @param resource       the requested resource
	 * @param method         the endpoint method
	 * @param configLocation the ORA configuration location
	 * @return the response cookie
	 * @throws AccessException
	 */
	public String authenticateUser(String userName, String password, String protocol, String resource, String method,
			String configLocation) throws AccessException {
		initAccessClient(configLocation, protocol, resource, method);
		String responseCookie = null;
		Hashtable<String, String> credentials = new Hashtable<>();
		credentials.put("userid", userName);
		credentials.put("password", password);
		UserSession session = new UserSession(rrq, credentials);
		if (session.getStatus() == LOGGEDIN) {
			if (session.isAuthorized(rrq)) {
				logger.info(
						"ORAAccessClient::authenticateUser > User is logged in and authorized for the request at level {}",
						session.getLevel());
				responseCookie = session.getSessionToken();
				logger.info("ORAAccessClient::authenticateUser > Session token: {}", responseCookie);
			} else {
				logger.warn("ORAAccessClient::authenticateUser > User is logged in but NOT authorized");
			}
		} else {
			logger.warn("ORAAccessClient::authenticateUser > User is NOT logged in");
		}
		return responseCookie;
	}

	/**
	 * Initialises ORA access client
	 *
	 * @param configLocation the ORA configuration location
	 * @param protocol       the protocol
	 * @param resource       the requested resource
	 * @param method         the endpoint method
	 * @throws AccessException
	 */
	private void initAccessClient(String configLocation, String protocol, String resource, String method)
			throws AccessException {
		if (ac == null) {
			ac = AccessClient.createDefaultInstance(configLocation, AccessClient.CompatibilityMode.OAM_10G);
			logger.info("ORAAccessClient::initAccessClient > Initialising ORA access client.");
		}

		if (rrq == null) {
			rrq = new ResourceRequest(protocol, resource, method);
		}

		if (rrq.isProtected()) {
			logger.warn("ORAAccessClient::initAccessClient > Resource is protected.");
			AuthenticationScheme authScheme = new AuthenticationScheme(rrq);
			if (authScheme.isForm()) {
				logger.info("ORAAccessClient::initAccessClient > Form Authentication Scheme.");
			} else {
				logger.info("ORAAccessClient::initAccessClient > non-Form Authentication Scheme.");
			}
		} else {
			logger.warn("ORAAccessClient::initAccessClient > Resource is NOT protected.");
		}
	}
}