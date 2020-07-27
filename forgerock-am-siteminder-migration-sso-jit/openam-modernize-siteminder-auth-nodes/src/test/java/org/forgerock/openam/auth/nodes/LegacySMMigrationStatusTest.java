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
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_PASSWORD_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.OPEN_IDM_ADMIN_USERNAME_HEADER;
import static org.forgerock.openam.modernize.utils.NodeConstants.QUERY_IDM_QUERY_USER_PATH;
import static org.mockito.BDDMockito.given;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.model.JsonBody.json;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import org.forgerock.http.Client;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.LegacySMMigrationStatus;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext.Builder;
import org.forgerock.openam.auth.node.api.NodeProcessException;
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

public class LegacySMMigrationStatusTest {

	@Mock
	LegacySMMigrationStatus.Config config;

	@Mock
	Realm realm;

	@Mock
	Secrets secrets;

	@Mock
	private AnnotatedServiceRegistry annotatedServiceRegistry;

	private LegacySMMigrationStatus node;
	private JsonValue sharedState;

	private static final String USERNAME_VALUE = "jane.doe";
	private static final String OPENIDM_ADMIN_USER = "openidm-admin";
	private static final String OPENIDM_ADMIN_SECRET = "secret";
	private static final String LOCAL_IDM = "http://localhost:9050";
	private ClientAndServer mockServerClient;

	@BeforeMethod
	public void setup() throws Exception {
		initMocks(this);

		sharedState = org.forgerock.json.JsonValue.json(object(field(USERNAME, USERNAME_VALUE)));

		given(config.idmPasswordId()).willReturn(OPENIDM_ADMIN_SECRET);
		given(config.idmUserEndpoint()).willReturn(LOCAL_IDM);
		given(config.idmAdminUser()).willReturn(OPENIDM_ADMIN_USER);

		mockServerClient = ClientAndServer.startClientAndServer(9050);

		node = new LegacySMMigrationStatus(config, realm, secrets, new HttpClientHandler()) {
			public boolean getUserMigrationStatus(String userName, String idmEndpoint, String idmAdmin,
					String idmPassword, HttpClientHandler httpClientHandler) throws NodeProcessException, IOException {
				String getUserPathWithQuery = idmEndpoint + QUERY_IDM_QUERY_USER_PATH + "\'" + userName + "\'";
				System.out
						.println("AbstractLegacyMigrationStatusNode::getUserMigrationStatus() > getUserPathWithQuery: "
								+ getUserPathWithQuery);
				Response response = getUser(getUserPathWithQuery, idmAdmin, idmPassword, httpClientHandler);
				if (response != null) {
					JsonValue jsonValues = JsonValue.json(response.getEntity().getJson());
					System.out.println(
							"AbstractLegacyMigrationStatusNode::getUserMigrationStatus() > jsonValues: " + jsonValues);
					return getUserMigrationStatus(jsonValues);
				}
				return false;
			}

			@SuppressWarnings("resource")
			private Response getUser(String endpoint, String idmAdmin, String idmPassword,
					HttpClientHandler httpClientHandler) {
				Request request = new Request();
				try {
					request.setMethod("GET").setUri(endpoint);
				} catch (URISyntaxException e) {
					System.out.println("AbstractLegacyMigrationStatusNode::getuser() > URISyntaxException: " + e);
				}
				request.getHeaders().add(OPEN_IDM_ADMIN_USERNAME_HEADER, idmAdmin);
				request.getHeaders().add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
				request.getHeaders().add("Content-Type", "application/json");
				Client client = new Client(httpClientHandler);
				Promise<Response, NeverThrowsException> responsePromise = client.send(request);
				return new Response(Status.OK).setEntity(json(
						"{\"result\":[{\"_id\":\"13a7e3dc-d895-46d2-82fd-732ac2c1067e\",\"_rev\":\"2\",\"mail\":\"marian.tiris@itsmartsystems.eu\",\"givenName\":\"kp-test\",\"sn\":\"kp-test\",\"userName\":\"kp-test\",\"accountStatus\":\"active\",\"effectiveRoles\":[],\"effectiveAssignments\":[]}],\"resultCount\":1,\"pagedResultsCookie\":null,\"totalPagedResultsPolicy\":\"NONE\",\"totalPagedResults\":-1,\"remainingPagedResults\":-1}",
						MediaType.APPLICATION_JSON_UTF_8).toString());
			}
		};

	}

	@Test
	public void testSuccessUserMigrationVerification() throws Exception {
		// Given
		sharedState = org.forgerock.json.JsonValue.json(object(field(USERNAME, USERNAME_VALUE)));

		mockServerClient
				.when(HttpRequest.request().withMethod("GET").withPath("/openidm/managed/user"), Times.exactly(1))
				.respond(HttpResponse.response().withStatusCode(200)
						.withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString())
						.withBody(json(
								"{\"result\":[{\"_id\":\"13a7e3dc-d895-46d2-82fd-732ac2c1067e\",\"_rev\":\"2\",\"mail\":\"marian.tiris@itsmartsystems.eu\",\"givenName\":\"kp-test\",\"sn\":\"kp-test\",\"userName\":\"kp-test\",\"accountStatus\":\"active\",\"effectiveRoles\":[],\"effectiveAssignments\":[]}],\"resultCount\":1,\"pagedResultsCookie\":null,\"totalPagedResultsPolicy\":\"NONE\",\"totalPagedResults\":-1,\"remainingPagedResults\":-1}",
								MediaType.APPLICATION_JSON_UTF_8).toString())
						.withDelay(TimeUnit.SECONDS, 1));

		// When
		Action result = node.process(getContext(sharedState));

		// Then
		assertThat(result.outcome).isEqualTo("true");
	}

	@Test
	public void testFailedUserMigrationVerification() throws Exception {
		// Given
		sharedState = org.forgerock.json.JsonValue.json(object(field(USERNAME, USERNAME_VALUE)));

		mockServerClient
				.when(HttpRequest.request().withMethod("GET").withPath("/openidm/managed/user"), Times.exactly(1))
				.respond(HttpResponse.response().withStatusCode(200)
						.withHeader(CONTENT_TYPE.toString(), MediaType.APPLICATION_JSON_UTF_8.toString())
						.withBody(json("{\"code\":401,\"reason\":\"Unauthorized\",\"message\":\"Access Denied\"}",
								MediaType.APPLICATION_JSON_UTF_8).toString())
						.withDelay(TimeUnit.SECONDS, 1));

		node = new LegacySMMigrationStatus(config, realm, secrets, new HttpClientHandler()) {
			public boolean getUserMigrationStatus(String userName, String idmEndpoint, String idmAdmin,
					String idmPassword, HttpClientHandler httpClientHandler) throws NodeProcessException, IOException {
				String getUserPathWithQuery = idmEndpoint + QUERY_IDM_QUERY_USER_PATH + "\'" + userName + "\'";
				System.out
						.println("AbstractLegacyMigrationStatusNode::getUserMigrationStatus() > getUserPathWithQuery: "
								+ getUserPathWithQuery);
				Response response = getUser(getUserPathWithQuery, idmAdmin, idmPassword, httpClientHandler);
				if (response != null) {
					JsonValue jsonValues = JsonValue.json(response.getEntity().getJson());
					System.out.println(
							"AbstractLegacyMigrationStatusNode::getUserMigrationStatus() > jsonValues: " + jsonValues);
					return getUserMigrationStatus(jsonValues);
				}
				return false;
			}

			@SuppressWarnings("resource")
			private Response getUser(String endpoint, String idmAdmin, String idmPassword,
					HttpClientHandler httpClientHandler) {
				Request request = new Request();
				try {
					request.setMethod("GET").setUri(endpoint);
				} catch (URISyntaxException e) {
					System.out.println("AbstractLegacyMigrationStatusNode::getuser() > URISyntaxException: " + e);
				}
				request.getHeaders().add(OPEN_IDM_ADMIN_USERNAME_HEADER, idmAdmin);
				request.getHeaders().add(OPEN_IDM_ADMIN_PASSWORD_HEADER, idmPassword);
				request.getHeaders().add("Content-Type", "application/json");
				Client client = new Client(httpClientHandler);
				Promise<Response, NeverThrowsException> responsePromise = client.send(request);
				return new Response(Status.UNAUTHORIZED)
						.setEntity(json("{\"code\":401,\"reason\":\"Unauthorized\",\"message\":\"Access Denied\"}",
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

	@AfterMethod
	public void unInitMocks() {
		mockServerClient.stop();
	}

}
