/***************************************************************************
 *  Copyright 2020 ForgeRock AS
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
package org.forgerock.openam.auth.nodes;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.PASSWORD;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_PASSWORD_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_USERNAME_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.PATCH_IDM_USER_PATH;
import static org.mockito.BDDMockito.given;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.JsonBody.json;

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import org.forgerock.http.Client;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.LegacySMSetPassword;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext.Builder;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.secrets.Secrets;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.mockito.Mock;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LegacySMSetPasswordTest {

	@Mock
	LegacySMSetPassword.Config config;

	@Mock
	Realm realm;

	@Mock
	Secrets secrets;

	@Mock
	private AnnotatedServiceRegistry annotatedServiceRegistry;

	private LegacySMSetPassword node;
	private JsonValue sharedState;

	private static final String USERNAME_VALUE = "jane.doe";
	private static final String PASSWORD_VALUE = "123456";
	private static final String OPENIDM_ADMIN_USER = "openidm-admin";
	private static final String OPENIDM_ADMIN_SECRET = "secret";
	private static final String LOCAL_IDM = "http://localhost:9060";
	private ClientAndServer mockServerClient;

	@BeforeMethod
	public void setup() throws Exception {
		initMocks(this);

		sharedState = org.forgerock.json.JsonValue
				.json(object(field(USERNAME, USERNAME_VALUE), field(PASSWORD, PASSWORD_VALUE)));

		given(config.idmPassworSecretdId()).willReturn(OPENIDM_ADMIN_SECRET);
		given(config.idmUserEndpoint()).willReturn(LOCAL_IDM);
		given(config.idmAdminUser()).willReturn(OPENIDM_ADMIN_USER);

		mockServerClient = ClientAndServer.startClientAndServer(9060);

		node = new LegacySMSetPassword(config, realm, secrets, new HttpClientHandler()) {
			public boolean setUserPassword(String userName, String password, String idmEndpoint, String idmAdmin,
					String idmPassword, HttpClientHandler httpClientHandler) {
				JsonValue jsonBody = createPasswordRequestEntity(password);
				Response response = callUpdatePassword(idmEndpoint + PATCH_IDM_USER_PATH + "'" + userName + "'",
						idmAdmin, idmPassword, jsonBody, httpClientHandler);
				if (response != null && response.getStatus().isSuccessful()) {
					return true;
				}
				return false;
			}

			/**
			 * Test sending request and receiving response separately
			 */
			@SuppressWarnings("resource")
			private Response callUpdatePassword(String endpoint, String idmAdmin, String idmPassword,
					JsonValue jsonBody, HttpClientHandler httpClientHandler) {
				Request request = new Request();
				try {
					request.setMethod("POST").setUri(endpoint);
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
				request.getHeaders().add(OPEN_IDM_ADMIN_USERNAME_HEADER, idmAdmin);
				request.getHeaders().add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
				request.getHeaders().add("Content-Type", "application/json");
				request.setEntity(jsonBody);
				Client client = new Client(httpClientHandler);
				Promise<Response, NeverThrowsException> responsePromise = client.send(request);
				return new Response(Status.OK).setEntity(json(
						"{'givenName':'jane.doe','sn':'jane.doe','mail':'mail@test.eu','userName':'jane.doe','accountStatus':'active','effectiveRoles':[],'effectiveAssignments':[],'_id':'ff2fa374-d096-48ef-af36-26c53eb48df4','_rev':'3'}",
						MediaType.APPLICATION_JSON_UTF_8).toString());
			}
		};

	}

	@AfterMethod
	public void unInitMocks() {
		mockServerClient.stop();
	}

	@Test
	public void testSuccessfullUpdatePassword() throws Exception {
		// Given
		sharedState = org.forgerock.json.JsonValue
				.json(object(field(USERNAME, USERNAME_VALUE), field(PASSWORD, PASSWORD_VALUE)));

		mockServerClient
				.when(HttpRequest.request().withMethod("POST").withPath("/openidm/managed/user")
						.removeHeader("Content-Length").withHeader(CONTENT_TYPE.toString(),
								MediaType.APPLICATION_JSON_UTF_8.toString()),
						Times.exactly(1))
				.respond(HttpResponse.response().withStatusCode(200)
						.withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString())
						.withBody(json(
								"{'givenName':'test.marian2','sn':'test.marian2','mail':'mail@test.eu','userName':'test.marian2','accountStatus':'active','effectiveRoles':[],'effectiveAssignments':[],'_id':'ff2fa374-d096-48ef-af36-26c53eb48df4','_rev':'3'}",
								MediaType.APPLICATION_JSON_UTF_8).toString())
						.withDelay(TimeUnit.SECONDS, 1));

		// When
		Action result = node.process(getContext(sharedState));

		// Then
		assertThat(result.outcome).isEqualTo("true");
	}

	@Test
	public void testfailedUpdatePasswordWrongAdminCredentials() throws Exception {
		// Given
		sharedState = org.forgerock.json.JsonValue
				.json(object(field(USERNAME, USERNAME_VALUE), field(PASSWORD, PASSWORD_VALUE)));

		mockServerClient
				.when(HttpRequest.request().withMethod("POST").withPath("/openidm/managed/user")
						.removeHeader("Content-Length").withHeader(CONTENT_TYPE.toString(),
								MediaType.APPLICATION_JSON_UTF_8.toString()),
						Times.exactly(1))
				.respond(HttpResponse.response().withStatusCode(401)
						.withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString())
						.withBody(json("{'code':401,'reason':'Unauthorized','message':'Access Denied'}",
								MediaType.APPLICATION_JSON_UTF_8).toString())
						.withDelay(TimeUnit.SECONDS, 1));

		node = new LegacySMSetPassword(config, realm, secrets, new HttpClientHandler()) {
			public boolean setUserPassword(String userName, String password, String idmEndpoint, String idmAdmin,
					String idmPassword, HttpClientHandler httpClientHandler) {
				JsonValue jsonBody = createPasswordRequestEntity(password);
				Response response = callUpdatePassword(idmEndpoint + PATCH_IDM_USER_PATH + "'" + userName + "'",
						idmAdmin, idmPassword, jsonBody, httpClientHandler);
				if (response != null && response.getStatus().isSuccessful()) {
					return true;
				}
				return false;
			}

			/**
			 * Test sending request and receiving response separately
			 */
			@SuppressWarnings("resource")
			private Response callUpdatePassword(String endpoint, String idmAdmin, String idmPassword,
					JsonValue jsonBody, HttpClientHandler httpClientHandler) {
				Request request = new Request();
				try {
					request.setMethod("POST").setUri(endpoint);
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
				request.getHeaders().add(OPEN_IDM_ADMIN_USERNAME_HEADER, idmAdmin);
				request.getHeaders().add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
				request.getHeaders().add("Content-Type", "application/json");
				request.setEntity(jsonBody);
				Client client = new Client(httpClientHandler);
				Promise<Response, NeverThrowsException> x = client.send(request);
				return new Response(Status.UNAUTHORIZED)
						.setEntity(json("{'code':401,'reason':'Unauthorized','message':'Access Denied'}",
								MediaType.APPLICATION_JSON_UTF_8).toString());
			}
		};

		// When
		Action result = node.process(getContext(sharedState));

		// Then
		assertThat(result.outcome).isEqualTo("false");
	}

	private TreeContext getContext(JsonValue sharedState) {
		return new TreeContext(sharedState, new JsonValue(""), new Builder().build(), emptyList());
	}

}
