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

package org.forgerock.openam.authentication.modules.siteminder;

import com.iplanet.sso.SSOException;
import com.netegrity.policyserver.smapi.SmAuthScheme;
import com.netegrity.policyserver.smapi.SmAuthQueryResponse;
import com.netegrity.policyserver.smapi.SmAuthenticationResult;
import com.netegrity.policyserver.smapi.APIContext;
import com.netegrity.policyserver.smapi.SmAuthStatus;
import com.netegrity.policyserver.smapi.SmAuthenticationContext;
import com.netegrity.policyserver.smapi.UserCredentialsContext;
import com.netegrity.policyserver.smapi.SmAuthQueryCode;
import com.netegrity.policyserver.smapi.UserContext;
import com.iplanet.sso.SSOTokenManager;
import com.iplanet.sso.SSOToken;

/**
 * The class <code>OpenAMAuthScheme</code> implements SiteMinder authentication
 * scheme <code>SMAuthScheme</code>. The OpenAMAuthScheme generates SiteMinder
 * session by consuming OpenAM session. SiteMinder is the trade mark of Computer
 * Associates, the usage of the SiteMinder API is subject to SiteMinder License
 * terms.
 * 
 * OpenAM authentication scheme for SiteMinder policy server. This allows user
 * to authenticate to SiteMinder with their OpenAM session.
 */
public class OpenAMAuthScheme implements SmAuthScheme {
	private static final int SCHEME_VERSION = SmAuthQueryResponse.SMAUTH_API_VERSION_V3;
	private static final String SCHEME_DESCRIPTION = "OpenAM Auth Scheme";

	static final String FMPREFIX = "FMTOKEN";
	static int fmprefixLen = FMPREFIX.length();
	boolean debug = false;

	/**
	 * Returns information about the authentication scheme.
	 *
	 * @param parameter - The parameter string as specified for the authentication
	 *                  scheme.
	 * @param secret    - The secret string as specified for the authentication
	 *                  scheme.
	 * @param request   - The request code, SMAUTH_QUERY_DESCRIPTION or
	 *                  SMAUTH_QUERY_CREDENTIALS_REQ.
	 * @param response  - Holds methods by which query() returns the requested
	 *                  information.
	 * @return SMAUTH_SUCCESS if successful otherwise SMAUTH_FAILURE
	 */
	@Override
	public SmAuthStatus query(String parameter, String secret, SmAuthQueryCode request, SmAuthQueryResponse response) {

		// Check for response buffer
		if (null == response) {
			return SmAuthStatus.SMAUTH_FAILURE;
		}

		// Scheme description request
		if (SmAuthQueryCode.SMAUTH_QUERY_DESCRIPTION == request) {
			response.setResponseBuffer(SCHEME_DESCRIPTION);
			response.setResponseCode(SCHEME_VERSION);
		}
		// Credentials type request
		else if (SmAuthQueryCode.SMAUTH_QUERY_CREDENTIALS_REQ == request) {
			response.setResponseCode(SmAuthQueryResponse.SMAUTH_CRED_BASIC);
		}
		// For anything else return failure
		else {
			return SmAuthStatus.SMAUTH_FAILURE;
		}

		return SmAuthStatus.SMAUTH_SUCCESS;
	}

	/**
	 * SiteMinder invokes this method so the authentication scheme can perform its
	 * own initialization procedure. This method is invoked once for each
	 * authentication scheme instance when it is first loaded.
	 *
	 * @param parameter The parameter string as specified for the authentication
	 *                  scheme.
	 * @param secret    The secret string as specified for the authentication
	 *                  scheme.
	 * @return: If successful returns SMAUTH_SUCCESS, otherwise returns
	 *          SMAUTH_FAILURE
	 */

	@Override
	public SmAuthStatus init(String parameter, String secret) {
		return SmAuthStatus.SMAUTH_SUCCESS;
	}

	/**
	 * SiteMinder invokes this method during shutdown so the authentication scheme
	 * can perform its own rundown procedure. This method is invoked once for each
	 * authentication scheme instance during SiteMinder shutdown.
	 *
	 * @param parameter The parameter string as specified for the authentication
	 *                  scheme.
	 * @param secret    The secret string as specified for the authentication scheme
	 * @return: If successful returns SMAUTH_SUCCESS, otherwise returns
	 *          SMAUTH_FAILURE
	 */

	@Override
	public SmAuthStatus release(String parameter, String secret) {
		return SmAuthStatus.SMAUTH_SUCCESS;
	}

	/**
	 * SiteMinder invokes this method to authenticate user credentials.
	 *
	 * @param parameter       The parameter string as specified for the
	 *                        authentication scheme.
	 * @param secret          The secret string as specified for the authentication
	 *                        scheme
	 * @param challengeReason The reason for the original authentication challenge,
	 *                        or 0 if unknown.
	 * @param context         Contains request context and methods to return message
	 *                        buffers.
	 *
	 * @return: an SmAuthenticationResult object.
	 */

	@Override
	public SmAuthenticationResult authenticate(String parameter, String secret, int challengeReason,
			SmAuthenticationContext context) {
		// Set debug mode
		debug = parameter.contains("debug");

		// cannot do anything without the authentication context
		if (context == null) {
			return new SmAuthenticationResult(SmAuthStatus.SMAUTH_FAILURE, SmAuthenticationResult.REASON_NONE);
		}

		APIContext apictx = context.getAPIContext();
		UserContext theUserContext = context.getUserContext();
		UserCredentialsContext theUserCredentialsContext = context.getUserCredentialsContext();

		// Debug logging
		if (debug && (theUserContext != null)) {
			apictx.log("UserContext is...");
			apictx.log("    isUserContext=" + theUserContext.isUserContext());
			apictx.log("    dirnamespace=" + theUserContext.getDirNameSpace());
			apictx.log("    dirserver=" + theUserContext.getDirServer());
			apictx.log("    dirpath=" + theUserContext.getDirPath());
			apictx.log("    username=" + theUserContext.getUserName());
			apictx.log("    userpath=" + theUserContext.getUserPath());
			apictx.log("    sessionid=" + theUserContext.getSessionID());
		}
		if (debug && (theUserCredentialsContext != null)) {
			apictx.log("UserCredentialsContext is...");
			apictx.log("    dirnamespace=" + theUserCredentialsContext.getDirNameSpace());
			apictx.log("    dirserver=" + theUserCredentialsContext.getDirServer());
			apictx.log("    dirpath=" + theUserCredentialsContext.getDirPath());
			apictx.log("    username=" + theUserCredentialsContext.getUserName());
			apictx.log("    passwd=" + theUserCredentialsContext.getPassword());
		}

		// Fail if there is no user context
		if (theUserContext == null) {
			apictx.error("OpenAMAuthScheme:authenticate() called without userContext");
			context.setUserText("OpenAMAuthScheme:authenticate() called without userContext");
			return new SmAuthenticationResult(SmAuthStatus.SMAUTH_FAILURE, SmAuthenticationResult.REASON_NONE);
		}

		String uid = theUserContext.getUserName();

		// Fail if there is no user credentials context
		if (theUserCredentialsContext == null) {
			apictx.error("OpenAMAuthScheme:authenticate() called without userCredentialsContext");
			context.setUserText("OpenAMAuthScheme:authenticate() called without userCredentialsContext");
			return new SmAuthenticationResult(SmAuthStatus.SMAUTH_FAILURE, SmAuthenticationResult.REASON_NONE);
		}

		String credentials = theUserCredentialsContext.getPassword();

		// Pass1 : Disambiguation Phase
		if (!theUserContext.isUserContext()) {
			if (debug) {
				apictx.log("OpenAMAuthScheme:authenticate() disambiguation phase, user=" + uid);
			}
			if (uid != null) {
				String fmuser = verifyFMToken(apictx, credentials);
				if (debug) {
					apictx.log("fmuser from verifyFMToken=" + fmuser);
				}
				if (fmuser != null) {
					// Extract the userid from the universal ID string
					int oupos = fmuser.indexOf(",ou=user,");
					if (fmuser.startsWith("id=") && (oupos > 0)) {
						fmuser = fmuser.substring(3, oupos);
						if (debug) {
							apictx.log("Returning SMAUTH_SUCCESS, user=" + fmuser);
						}
						context.setUserText(fmuser);
						return new SmAuthenticationResult(SmAuthStatus.SMAUTH_SUCCESS,
								SmAuthenticationResult.REASON_NONE);
					} else {
						// DN doesn't seem to be OpenAM universal ID
						if (debug) {
							apictx.log("Returning SMAUTH_SUCCESS_USER_DN, user=" + fmuser);
						}
						context.setUserText(fmuser);
						return new SmAuthenticationResult(SmAuthStatus.SMAUTH_SUCCESS_USER_DN,
								SmAuthenticationResult.REASON_NONE);
					}
				}
			}
			apictx.error(
					"OpenAMAuthScheme:authenticate() failed to set valid userid from token, returning SMAUTH_FAILURE");
			context.setUserText("OpenAMAuthScheme:authenticate() failed to set valid userid from token");
			return new SmAuthenticationResult(SmAuthStatus.SMAUTH_FAILURE, SmAuthenticationResult.REASON_NONE);
		}

		// Pass2 : Authentication Phase

		// Reject the user if the password is not entered.
		if (credentials.length() <= 0) {
			apictx.error("OpenAMAuthScheme:authenticate() called without supplied token");
			return new SmAuthenticationResult(SmAuthStatus.SMAUTH_REJECT, SmAuthenticationResult.REASON_NONE);
		}

		// Check if the user account is disabled.
		try {
			if (0 != Integer.parseInt(theUserContext.getProp("disabled"))) {
				apictx.log("Account disabled, user=" + theUserContext.getUserName());
				context.setUserText("User account is disabled.");
				return new SmAuthenticationResult(SmAuthStatus.SMAUTH_REJECT,
						SmAuthenticationResult.REASON_USER_DISABLED);
			}
		} catch (NumberFormatException exc) {
			// Do nothing -- the user is not disabled
		}

		// authenticate the user
		Boolean isAuthenticated;

		String fmuser = verifyFMToken(apictx, credentials);
		if (fmuser != null) {
			// Check that the userid in userCredentialsContext
			// is the same as the one extracted from the token
			if (fmuser.indexOf("id=" + theUserCredentialsContext.getUserName() + ",ou=user,") == 0) {
				isAuthenticated = true;
				if (debug) {
					apictx.log("OpenAMAuthScheme: FMToken is valid");
				}
			} else {
				isAuthenticated = false;
				apictx.log("OpenAMAuthScheme: userid in token does not match userid in userCredentialsContext");
				apictx.log("userCredentialsContext.userid=" + theUserCredentialsContext.getUserName()
						+ ", token.userid=" + fmuser);
			}
		} else {
			isAuthenticated = false;
			apictx.log("OpenAMAuthScheme: FMToken is invalid: SMAUTH_REJECT");
		}

		if (Boolean.TRUE.equals(isAuthenticated)) {
			if (debug) {
				apictx.log("OpenAMAuthScheme:authenticate() end, returning SMAUTH_ACCEPT");
			}
			return new SmAuthenticationResult(SmAuthStatus.SMAUTH_ACCEPT, SmAuthenticationResult.REASON_NONE);
		} else {
			if (debug) {
				apictx.log("OpenAMAuthScheme:authenticate() end, returning SMAUTH_REJECT");
			}
			context.setErrorText("Unable to authenticate user " + theUserContext.getUserName());

			return new SmAuthenticationResult(SmAuthStatus.SMAUTH_REJECT, SmAuthenticationResult.REASON_NONE);
		}
	}

	private String verifyFMToken(APIContext apictx, String cookie) {
		if (cookie == null) {
			if (debug) {
				apictx.error("verifyFMToken() cookie == null");
			}
			return null;
		}
		if (!cookie.startsWith(FMPREFIX)) {
			apictx.error("verifyFMToken() invalid token supplied, token=" + cookie);
			return null;
		}
		cookie = cookie.substring(fmprefixLen, cookie.length());
		SSOToken token;
		try {
			SSOTokenManager manager = SSOTokenManager.getInstance();
			token = manager.createSSOToken(cookie);
			if (!manager.isValidToken(token)) {
				// Token is not valid, return null
				if (debug) {
					apictx.log("verifyFMToken() Token is not valid");
				}
				return null;
			}
			// Token is valid, return username
			if (debug) {
				apictx.log("verifyFMToken() Token is valid");
			}
			return token.getPrincipal().getName();
		} catch (SSOException | UnsupportedOperationException ex2) {
			apictx.error("verifyFMToken() caught Exception=" + ex2.getMessage());
		}
		return null;
	}

}
