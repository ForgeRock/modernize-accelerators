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
package org.forgerock.openig.miami.filter;

import static org.forgerock.openig.el.Bindings.bindings;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Message;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.openig.miami.utils.RequestUtils;
import org.forgerock.openig.miami.utils.SimpleUserWrapper;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.forgerock.util.promise.Promises;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class HeaderAuthenticationProvisioningFilter implements Filter {

	private static final String IDM_CREATE_USER_PATH = "/openidm/managed/user?_action=create";
	private static final String IDM_GET_USER_PATH = "/openidm/managed/user?_queryFilter=userName+eq+\"{0}\"";
	private static final String AM_GET_USER_DETAILS_PATH = "/json/realms/root/realms/legacy/users/";
	private static final String AM_COOKIE_NAME = "iPlanetDirectoryPro";

	private Logger LOGGER = LoggerFactory.getLogger(HeaderAuthenticationProvisioningFilter.class);

	private String idmURL;
	private String openIdmPassword;
	private String openIdmUsername;
	private String openaAmAuthenticateURL;
	private String legacyEnvURL;

	@Override
	public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
		LOGGER.error("filter()::request - START");
		String requestMedhod = request.getMethod();
		if (requestMedhod != null && "post".equalsIgnoreCase(requestMedhod)) {
			LOGGER.error("filter()::POST authentication call detected");
			try {
				String requestBody = request.getEntity().getString();
				if (requestBody != null) {
					LOGGER.error("filter()::Reading authentication callbacks on request.");
					SimpleUserWrapper userInfo = getUserInfoFromRequest(requestBody);
					if (userInfo != null) {
						// ============================================
						// USER MIGRATED - START
						// ============================================
						boolean userMigrated = getUserMigrationStatus(userInfo);
						if (userMigrated) {
							String callback = getCallbacks(openaAmAuthenticateURL);
							String responseCookie = null;
							if (callback != null && !callback.isEmpty()) {
								String callbackBody = createAuthenticationCallbacks(callback, userInfo.getUserName(),
										userInfo.getUserPassword());
								responseCookie = getCookie(openaAmAuthenticateURL, callbackBody);
							}

							if (responseCookie != null) {
								String requestCookie = responseCookie;
								Promise<Response, NeverThrowsException> promise = next.handle(context, request);
								return promise.thenOnResult(response -> processSecondLogin(response,
										bindings(context, request, response), requestCookie));
							} else {
								LOGGER.error("Authentication failed. Username or password invalid.");
								Response response = new Response(Status.UNAUTHORIZED);
								response.setEntity(createFailedLoginError());
								return Promises.newResultPromise(response);
							}
							// ============================================
							// USER MIGRATED - END
							// ============================================
						} else {
							// ============================================
							// USER NOT MIGRATED
							// ============================================
							LOGGER.error("filter()::Intercepted user credentials from the log in request: "
									+ userInfo.toString());
							Promise<Response, NeverThrowsException> promise = next.handle(context, request);
							return promise.thenOnResult(response -> processFirstLoginWithAttributes(response,
									bindings(context, request, response), userInfo));
						}
					}
				}
			} catch (IOException e) {
				LOGGER.error("filter()::IOException: " + e.getMessage());
				e.printStackTrace();
			}
		}
		return next.handle(context, request);
	}

	private SimpleUserWrapper getUserInfoFromRequest(String requestBody) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode callbacks = null;
		callbacks = mapper.readTree(requestBody);
		if (callbacks != null) {
			SimpleUserWrapper userInfo = new SimpleUserWrapper();
			userInfo.setUserName(callbacks.get("callbacks").get(0).get("input").get(0).get("value").asText());
			userInfo.setUserPassword(callbacks.get("callbacks").get(1).get("input").get(0).get("value").asText());
			return userInfo;
		}
		return null;
	}

	private boolean getUserMigrationStatus(SimpleUserWrapper userInfo) throws IOException {
		String getUserPathWithQuery = idmURL + MessageFormat.format(IDM_GET_USER_PATH, userInfo.getUserName());
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add("X-OpenIDM-Password", openIdmPassword);
		headersMap.add("X-OpenIDM-Username", openIdmUsername);
		ResponseEntity<String> responseEntity = RequestUtils.sendGetRequest(getUserPathWithQuery,
				MediaType.APPLICATION_JSON, headersMap);
		return (getUserMigrationStatus(responseEntity));
	}

	private boolean getUserMigrationStatus(ResponseEntity<String> responseEntity) throws IOException {
		LOGGER.debug("getUserMigrationStatus()::response.getBody(): " + responseEntity.getBody());
		ObjectMapper mapper = new ObjectMapper();
		JsonNode responseNode = mapper.readTree(responseEntity.getBody());
		if (responseNode != null && responseNode.get("resultCount") != null
				&& responseNode.get("resultCount").asInt() > 0) {
			return true;
		}
		return false;
	}

	private String getCallbacks(String url) {
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add("Accept-API-Version", "resource=2.0, protocol=1.0");
		ResponseEntity<String> responseEntity = RequestUtils.sendPostRequest(url, null, MediaType.APPLICATION_JSON,
				headersMap);
		LOGGER.debug("getCallbacks()::response.getBody(): " + responseEntity.getBody());
		return responseEntity.getBody();
	}

	private String getCookie(String url, String jsonBody) {
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add("Accept-API-Version", "resource=2.0, protocol=1.0");
		ResponseEntity<String> responseEntity = RequestUtils.sendPostRequest(url, jsonBody, MediaType.APPLICATION_JSON,
				headersMap);
		if (responseEntity != null) {
			LOGGER.debug("getCookie()::response.getBody(): " + responseEntity.getBody());
			HttpHeaders responseHeaders = responseEntity.getHeaders();
			List<String> cookies = responseHeaders.get("Set-Cookie");
			String cookie = cookies.stream().filter(x -> x.contains(AM_COOKIE_NAME)).findFirst().orElse(null);
			LOGGER.error("getCookie()::Cookie: " + cookie);
			return cookie;
		}
		return null;
	}

	private String createAuthenticationCallbacks(String callback, String userId, String password) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode callbackNode = mapper.createObjectNode();
		callbackNode = (ObjectNode) mapper.readTree(callback);
		ObjectNode nameCallback = (ObjectNode) callbackNode.get("callbacks").get(0).get("input").get(0);
		nameCallback.put("value", userId);
		ObjectNode passwordCallback = (ObjectNode) callbackNode.get("callbacks").get(1).get("input").get(0);
		passwordCallback.put("value", password);
		return callbackNode.toString();
	}

	private void processFirstLoginWithAttributes(Message<?> message, Bindings bindings, SimpleUserWrapper userInfo) {
		LOGGER.error("processFirstLoginWithAttributes()::Received authentication response.");
		LOGGER.error("processFirstLoginWithAttributes()::userInfo: " + userInfo);

		Map<String, List<String>> responseHeadersMap = message.getHeaders().copyAsMultiMapOfStrings();
		List<String> cookieValues = responseHeadersMap.get("Set-Cookie");
		String cookieForProfileDetails = null;
		for (String cookie : cookieValues) {
			if (cookie != null && cookie.contains("rsaSso")) {
				cookieForProfileDetails = cookie;
			}
		}

		if (cookieForProfileDetails != null) {
			MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
			headersMap.add("Cookie", cookieForProfileDetails);
			ResponseEntity<String> responseEntity = RequestUtils.sendGetRequest(
					legacyEnvURL + AM_GET_USER_DETAILS_PATH + userInfo.getUserName(), MediaType.APPLICATION_JSON,
					headersMap);

			String firstName = null;
			String lastName = null;
			String email = null;
			if (responseEntity != null) {
				JsonNode response = null;
				try {
					ObjectMapper mapper = new ObjectMapper();
					response = mapper.readTree(responseEntity.getBody());
				} catch (IOException e) {
					LOGGER.error("successfullLogin()::IOException: " + e.getMessage());
					e.printStackTrace();
				}
				if (response != null) {

					ArrayNode firstNameArrayNode = (ArrayNode) response.get("givenName");
					if (firstNameArrayNode != null) {
						firstName = firstNameArrayNode.get(0).asText();
					}

					ArrayNode lastNameArrayNode = (ArrayNode) response.get("sn");
					if (lastNameArrayNode != null) {
						lastName = lastNameArrayNode.get(0).asText();
					}

					ArrayNode mailArrayNode = (ArrayNode) response.get("mail");
					if (mailArrayNode != null) {
						email = mailArrayNode.get(0).asText();
					}
				}
			}
			LOGGER.debug("processFirstLoginWithAttributes()::Detected successfull login in Legacy system");
			LOGGER.debug("processFirstLoginWithAttributes()::Retrieving user details from Legacy system");
			provisionUser(userInfo, firstName, lastName, email);
		}
	}

	private void processSecondLogin(Message<?> message, Bindings bindings, String cookie) {
		LOGGER.error("processSecondLogin()::Received authentication response - Setting cookie.");
		message.getHeaders().add("Set-Cookie", cookie);
	}

	private void provisionUser(SimpleUserWrapper userInfo, String firstName, String lastName, String email) {
		LOGGER.error("provisionUser()::Start");
		String jsonBody = createProvisioningRequestEntity(userInfo, firstName, lastName, email);
		MultiValueMap<String, String> headersMap = new LinkedMultiValueMap<>();
		headersMap.add("X-OpenIDM-Username", openIdmUsername);
		headersMap.add("X-OpenIDM-Password", openIdmPassword);
		RequestUtils.sendPostRequest(idmURL + IDM_CREATE_USER_PATH, jsonBody, MediaType.APPLICATION_JSON, headersMap);
		LOGGER.error("provisionUser()::End");
	}

	private String createProvisioningRequestEntity(SimpleUserWrapper userInfo, String firstName, String lastName,
			String email) {
		LOGGER.error("createProvisioningRequestEntity()::userInfo: " + userInfo.toString());
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode node = mapper.createObjectNode();
		node.put("givenName", firstName);
		node.put("sn", lastName);
		node.put("mail", email);
		node.put("userName", userInfo.getUserName());
		node.put("password", userInfo.getUserPassword());
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

	private String createFailedLoginError() {
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

	/**
	 * Create and initialize the filter, based on the configuration. The filter
	 * object is stored in the heap.
	 */
	public static class Heaplet extends GenericHeaplet {

		/**
		 * Create the filter object in the heap, setting the header name and value for
		 * the filter, based on the configuration.
		 *
		 * @return The filter object.
		 * @throws HeapException Failed to create the object.
		 */
		@Override
		public Object create() throws HeapException {
			HeaderAuthenticationProvisioningFilter filter = new HeaderAuthenticationProvisioningFilter();
			filter.idmURL = config.get("idmURL").as(evaluatedWithHeapProperties()).asString();
			filter.openIdmPassword = config.get("openIdmPassword").as(evaluatedWithHeapProperties()).asString();
			filter.openIdmUsername = config.get("openIdmUsername").as(evaluatedWithHeapProperties()).asString();
			filter.openaAmAuthenticateURL = config.get("openaAmAuthenticateURL").as(evaluatedWithHeapProperties())
					.asString();
			filter.legacyEnvURL = config.get("legacyEnvURL").as(evaluatedWithHeapProperties()).asString();
			return filter;
		}
	}

}
