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
package org.forgerock.openig.modernize;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;

import java.util.Map;

public interface LegacyIAMProvider {

	/**
	 * 
	 * Implementation must read the user credentials from the Forgerock HTTP
	 * Request. The HTTP request gives flexibility to capture the user's credentials
	 * from the request headers or from the request body. Should output a User
	 * object with the intercepted username and password.
	 * 
	 * <br>
	 * <br>
	 * <b>Example for getting the request body:</b>
	 * 
	 * <code>
	   request.getEntity().getString()
	   </code>
	 * 
	 * <br>
	 * <b>Example for getting the request headers:</b>
	 * 
	 * <code>
	   request.getHeaders()
	   </code>
	 * 
	 * @param request - ForgeRock HTTP {@link Request}
	 * 
	 * @return {@link User} - An user object with userName and userPassword set.
	 * @throws Exception - in case of any error
	 * 
	 */
	JsonValue getUserCredentials(Request request) throws Exception;

	/**
	 * 
	 * Get user profile attributes from the legacy IAM, with userName as input.
	 * 
	 * @param response - ForgeRock HTTP {@link Response}
	 * @param userName - The user for which to retrieve the profile attributes
	 * @return {@link User} - An user object with userName and userPassword set.
	 */
	JsonValue getExtendedUserAttributes(Response response, String userName, Map<String, Object> userAttributesMapping);

	/**
	 * 
	 * Validate if the authentication response from the legacy system is
	 * successfull.
	 * 
	 * @param response - ForgeRock HTTP {@link Response}
	 * @return true if the authentication is successfull, false if authentication
	 *         failed
	 */
	boolean validateLegacyAuthResponse(Response response);
}