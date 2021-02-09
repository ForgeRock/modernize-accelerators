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
package org.forgerock.openig.modernize;

import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;

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
	 * @return - JsonValue describing the user object with set userName and userPassword.
	 */
	JsonValue getUserCredentials(Request request);

	/**
	 * 
	 * Get user profile attributes from the legacy IAM, with userName as input.
	 * 
	 * @param response 			- ForgeRock HTTP {@link Response}
	 * @param userName 			- user for which to retrieve the profile attributes
	 * @param httpClientHandler - http client handler for executing requests
	 * @return - An user object with set userName and userPassword.
	 */
	Promise<Response, NeverThrowsException> getExtendedUserAttributes(Response response, String userName,
			Handler httpClientHandler);
}
