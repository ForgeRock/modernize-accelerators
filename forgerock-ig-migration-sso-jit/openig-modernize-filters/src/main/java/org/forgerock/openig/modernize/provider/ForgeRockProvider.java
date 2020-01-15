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
package org.forgerock.openig.modernize.provider;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.List;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.modernize.common.RequestUtils;
import org.forgerock.openig.modernize.common.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class ForgeRockProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(ForgeRockProvider.class);

	/**
	 * 
	 * Method calls the client implementation to intercept the credentials from the
	 * request headers or body, and returns a {@link User} instance with filled
	 * userName and userPassword
	 * 
	 * @param legacyIAMProvider        - the implementation class instantiated
	 * @param request                  - ForgeRock HTTP {@link Request}
	 * @param getUserCredentialsMethod - Method name from the modernize library
	 * @return {@link User} - An user object with userName and userPassword set.
	 * 
	 */
	public static User getUserCredentials(Class<?> legacyIAMProvider, Request request,
			String getUserCredentialsMethod) {
		Method getUserCredentials = null;
		try {
			getUserCredentials = legacyIAMProvider.getMethod(getUserCredentialsMethod, Request.class);
		} catch (NoSuchMethodException e1) {
			LOGGER.error("getUserCredentials()::NoSuchMethodException: " + e1.getMessage());
			e1.printStackTrace();
		} catch (SecurityException e1) {
			LOGGER.error("getUserCredentials()::SecurityException: " + e1.getMessage());
			e1.printStackTrace();
		}
		if (getUserCredentials != null) {
			LOGGER.debug(
					"Retrieved method " + getUserCredentials.getName() + " from class " + legacyIAMProvider.getName());
			try {
				return (User) getUserCredentials.invoke(legacyIAMProvider.newInstance(), request);
			} catch (IllegalAccessException e) {
				LOGGER.error("getUserCredentials()::IllegalAccessException: " + e.getMessage());
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				LOGGER.error("getUserCredentials()::IllegalArgumentException: " + e.getMessage());
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				LOGGER.error("getUserCredentials()::InvocationTargetException: " + e.getCause());
				e.printStackTrace();
			} catch (InstantiationException e) {
				LOGGER.error("getUserCredentials()::InstantiationException: " + e.getMessage());
				e.printStackTrace();
			}
		}
		return null;
	}

	/**
	 * 
	 * Calls IDM to get the information needed to determine if the user is migrated
	 * 
	 * @param getUserMigrationStatusEndpoint - The IDM end-point where we check if
	 *                                       the user is migrated
	 * @param userName                       - The userName of the current
	 *                                       requesting user
	 * @param openIdmUsernameHeader          - The HTTP header name where to send
	 *                                       the IDM administrator user
	 * @param openIdmUsername                - The IDM administrator userName
	 * @param openIdmPasswordHeader          - The HTTP header name where to send
	 *                                       the IDM administrator user's password
	 * @param openIdmPassword                - The IDM administrator password
	 * @return - true if user is migrated, false otherwise
	 * @throws IOException
	 */
	public static boolean userMigrated(String getUserMigrationStatusEndpoint, String userName,
			String openIdmUsernameHeader, String openIdmUsername, String openIdmPasswordHeader, String openIdmPassword)
			throws IOException {
		String getUserPathWithQuery = MessageFormat.format(getUserMigrationStatusEndpoint, userName);
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add(openIdmUsernameHeader, openIdmUsername);
		headersMap.add(openIdmPasswordHeader, openIdmPassword);
		ResponseEntity<String> responseEntity = RequestUtils.sendGetRequest(getUserPathWithQuery,
				MediaType.APPLICATION_JSON, headersMap);
		return (userMigrated(responseEntity));
	}

	/**
	 * 
	 * Reads the response received from IDM and determines if user is migrated
	 * 
	 * @param responseEntity - the response received from IDM
	 * @return - true if user is migrated, false otherwise
	 * @throws IOException
	 */
	private static boolean userMigrated(ResponseEntity<String> responseEntity) throws IOException {
		LOGGER.debug("userMigrated()::response.getBody(): " + responseEntity.getBody());
		ObjectMapper mapper = new ObjectMapper();
		JsonNode responseNode = mapper.readTree(responseEntity.getBody());
		if (responseNode != null && responseNode.get("resultCount") != null
				&& responseNode.get("resultCount").asInt() > 0) {
			return true;
		}
		return false;
	}

	/**
	 * 
	 * Authenticates the user in AM and retrieves the SSO token that will be set on
	 * the response
	 * 
	 * @param                             {@link User} - user object that holds
	 *                                    credentials for authentication
	 * @param openaAmAuthenticateURL      - the authentication URL for OpenAM
	 * @param acceptApiVersionHeader      - Accept API Version header name
	 * @param acceptApiVersionHeaderValue - Accept API version header value
	 * @param openAmCookieName            - The cookie name for OpenAM - default
	 *                                    iPlanetDirectoryPro
	 * @return
	 */
	public static String authenticateUser(User user, String openaAmAuthenticateURL, String acceptApiVersionHeader,
			String acceptApiVersionHeaderValue, String openAmCookieName) {
		String callbacks = getCallbacks(openaAmAuthenticateURL, acceptApiVersionHeader, acceptApiVersionHeaderValue);
		if (callbacks != null && !callbacks.isEmpty()) {
			try {
				callbacks = createAuthenticationCallbacks(callbacks, user.getUserName(), user.getUserPassword());
			} catch (IOException e) {
				LOGGER.error("authenticateUser()::Error: " + e.getMessage());
				e.printStackTrace();
			}
			return getCookie(openaAmAuthenticateURL, callbacks, acceptApiVersionHeader, acceptApiVersionHeaderValue,
					openAmCookieName);
		}
		return null;
	}

	/**
	 * 
	 * Gets the callbacks from first authenticate request in OpenAM
	 * 
	 * @param                             openaAmAuthenticateURL- the authentication
	 *                                    URL for OpenAM
	 * @param acceptApiVersionHeader      - Accept API Version header name
	 * @param acceptApiVersionHeaderValue - Accept API version header value
	 * @return - JSON body callbacks received from OpenAM
	 */
	private static String getCallbacks(String openaAmAuthenticateURL, String acceptApiVersionHeader,
			String acceptApiVersionHeaderValue) {
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add(acceptApiVersionHeader, acceptApiVersionHeaderValue);
		ResponseEntity<String> responseEntity = RequestUtils.sendPostRequest(openaAmAuthenticateURL, null,
				MediaType.APPLICATION_JSON, headersMap);
		LOGGER.debug("getCallbacks()::response.getBody(): " + responseEntity.getBody());
		return responseEntity.getBody();
	}

	/**
	 * 
	 * Method that fills in the user credential in the authentication callbacks
	 * 
	 * @param callback - empty callback JSON string
	 * @param userId   - userName
	 * @param password - userPassword
	 * @return - JSON body callbacks received from OpenAM with credentials set
	 * @throws IOException
	 */
	private static String createAuthenticationCallbacks(String callback, String userId, String password)
			throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode callbackNode = mapper.createObjectNode();
		callbackNode = (ObjectNode) mapper.readTree(callback);
		ObjectNode nameCallback = (ObjectNode) callbackNode.get("callbacks").get(0).get("input").get(0);
		nameCallback.put("value", userId);
		ObjectNode passwordCallback = (ObjectNode) callbackNode.get("callbacks").get(1).get("input").get(0);
		passwordCallback.put("value", password);
		return callbackNode.toString();
	}

	/**
	 * 
	 * Sends the authentication request callbacks with credentials to OpenAM and
	 * extracts the SSO token from the authentication response headers
	 * 
	 * @param openaAmAuthenticateURL      - the authentication URL for OpenA
	 * @param callbacks                   - JSON body callbacks received from OpenAM
	 *                                    with credentials set
	 * @param acceptApiVersionHeader      - Accept API Version header name
	 * @param acceptApiVersionHeaderValue - Accept API version header value
	 * @param openAmCookieName            - The cookie name for OpenAM - default
	 *                                    iPlanetDirectoryPro
	 * @return - The SSO token received following the authentication
	 * 
	 */
	private static String getCookie(String openaAmAuthenticateURL, String callbacks, String acceptApiVersionHeader,
			String acceptApiVersionHeaderValue, String openAmCookieName) {
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add(acceptApiVersionHeader, acceptApiVersionHeaderValue);
		ResponseEntity<String> responseEntity = RequestUtils.sendPostRequest(openaAmAuthenticateURL, callbacks,
				MediaType.APPLICATION_JSON, headersMap);
		if (responseEntity != null) {
			LOGGER.debug("getCookie()::response.getBody(): " + responseEntity.getBody());
			HttpHeaders responseHeaders = responseEntity.getHeaders();
			List<String> cookies = responseHeaders.get("Set-Cookie");
			String cookie = cookies.stream().filter(x -> x.contains(openAmCookieName)).findFirst().orElse(null);
			LOGGER.error("getCookie()::Cookie: " + cookie);
			return cookie;
		}
		return null;
	}

	/**
	 * 
	 * Calls IDM user URL in order to create a user
	 * 
	 * @param user                  {@link User} - An user object with userName and
	 *                              userPassword set, and extended user profile
	 *                              attributes
	 * @param openIdmUsernameHeader - The HTTP header name where to send the IDM
	 *                              administrator user
	 * @param openIdmUsername       - The IDM administrator userName
	 * @param openIdmPasswordHeader - The HTTP header name where to send the IDM
	 *                              administrator user's password
	 * @param openIdmPassword       - The IDM administrator password
	 * @param provisionUserEndpoint - The IDM create user URL
	 * 
	 */
	public static void provisionUser(User user, String openIdmUsernameHeader, String openIdmUsername,
			String openIdmPasswordHeader, String openIdmPassword, String provisionUserEndpoint) {
		LOGGER.error("provisionUser()::Start");
		String jsonBody = createProvisioningRequestEntity(user);
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add(openIdmUsernameHeader, openIdmUsername);
		headersMap.add(openIdmPasswordHeader, openIdmPassword);
		RequestUtils.sendPostRequest(provisionUserEndpoint, jsonBody, MediaType.APPLICATION_JSON, headersMap);
		LOGGER.error("provisionUser()::End");
	}

	/**
	 * 
	 * Formats the create user request body as accepted by the create user end point
	 * 
	 * @param {@link User} - An user object with userName and userPassword set, and
	 *        extended user profile attributes
	 * @return - the user profile formatted as JSON
	 */
	private static String createProvisioningRequestEntity(User user) {
		LOGGER.error("createProvisioningRequestEntity()::user: " + user);
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode node = mapper.createObjectNode();
		node.put("givenName", user.getUserFirstName());
		node.put("sn", user.getUserLastName());
		node.put("mail", user.getUserEmail());
		node.put("userName", user.getUserName());
		node.put("password", user.getUserPassword());
		String jsonString = null;
		try {
			jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
		} catch (JsonProcessingException e) {
			LOGGER.error("createProvisioningRequestEntity()::Error creating provisioning entity: " + e.getMessage());
			e.printStackTrace();
		}
		LOGGER.debug("createProvisioningRequestEntity()::entity: " + jsonString);
		return jsonString;
	}

	/**
	 * 
	 * Creates an {@link User} object complete with extended user profile attributes
	 * 
	 * @param response             - The ForgeRock HTTP {@link Response}
	 * @param user                 - The user before getting the extended user
	 *                             profile atributes
	 * @param legacyIAMProvider    - the implementation class instantiated
	 * @param getUserProfileMethod - Method name from the modernize library
	 * @return {@link User} - An user object with userName userPassword, and
	 *         attributes set
	 */
	public static User getExtendedUserProfile(Response response, User user, Class<?> legacyIAMProvider,
			String getUserProfileMethod) {
		try {
			Method getExtendedUserProfile = legacyIAMProvider.getMethod(getUserProfileMethod, Response.class,
					String.class);
			if (getExtendedUserProfile != null) {
				LOGGER.debug("Retrieved method " + getExtendedUserProfile.getName() + " from class "
						+ legacyIAMProvider.getName());
				return (User) getExtendedUserProfile.invoke(legacyIAMProvider.newInstance(), response,
						user.getUserName());
			}
		} catch (Exception e) {
			LOGGER.error("successfullLegacyAuthentication()::Error invoking " + getUserProfileMethod + ": "
					+ e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 
	 * Validate if authentication in Legacy IAM was successful
	 * 
	 * @param response                           - The ForgeRock HTTP
	 *                                           {@link Response}
	 * @param legacyIAMProvider                  - the implementation class
	 *                                           instantiated
	 * @param validateLegacyAuthenticationMethod - Method name from the modernize
	 *                                           library
	 * @return
	 */
	public static boolean successfullLegacyAuthentication(Response response, Class<?> legacyIAMProvider,
			String validateLegacyAuthenticationMethod) {
		try {
			Method validateLegacyAuthResponse = legacyIAMProvider.getMethod(validateLegacyAuthenticationMethod,
					Response.class);
			if (validateLegacyAuthResponse != null) {
				LOGGER.debug("Retrieved method " + validateLegacyAuthResponse.getName() + " from class "
						+ legacyIAMProvider.getName());
				return (boolean) validateLegacyAuthResponse.invoke(legacyIAMProvider.newInstance(), response);
			}
		} catch (Exception e) {
			LOGGER.error("successfullLegacyAuthentication()::Error invoking " + validateLegacyAuthenticationMethod
					+ ": " + e.getMessage());
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * 
	 * Creates the error message response in case something failed during the
	 * authentication/migration process
	 * 
	 * @return response body formatter
	 */
	public static String createFailedLoginError() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode node = mapper.createObjectNode();
		node.put("code", 401);
		node.put("reason", "Unauthorized");
		node.put("message", "Authentication Failed");
		String jsonString = null;
		try {
			jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		LOGGER.error("createFailedLoginError()::entity: " + jsonString);
		return jsonString;
	}
}
