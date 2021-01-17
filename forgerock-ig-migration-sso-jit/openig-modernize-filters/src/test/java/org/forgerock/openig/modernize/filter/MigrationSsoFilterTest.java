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
package org.forgerock.openig.modernize.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.util.promise.Promises.newResultPromise;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.forgerock.http.Handler;
import org.forgerock.http.header.SetCookieHeader;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.http.protocol.Status;
import org.forgerock.services.context.AttributesContext;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class MigrationSsoFilterTest {

	private static final String LEGACY_COOKIE = "legacyCookie";

	@Mock
	private Handler handler;

	private AttributesContext attributesContext;
	private Context context;

	@BeforeMethod
	public void setUp() throws Exception {
		attributesContext = new AttributesContext(new RootContext());
		context = attributesContext;
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void testSuccessfulLoginToLegacyIAM() throws Exception {
		MigrationSsoFilter filter = new MigrationSsoFilter();
		Request request = newRequest();
		when(handler.handle(context, request)).then(invocation -> {
			final Response response = new Response(Status.OK);
			response.getHeaders().put(SetCookieHeader.NAME,
					"legacyCookie=bfb_G1jCHUXjxIefrc6cyKuiiOw.*AAJTSQACMDIAAlNLABxpZUdGZ3NGQTV6L1paV2NldlQ5RlJUUW93Yms9AAR0eXBlAANDVFMAAlMxAAIwMQ..*");
			return newResultPromise(response);
		});

		Response response = filter.filter(context, request, handler).get();
		assertThat(response.getStatus().equals(Status.OK));
		assertThat(response.getHeaders().get(SetCookieHeader.NAME)).isNotNull();
		Map<String, List<String>> headersMap = response.getHeaders().copyAsMultiMapOfStrings();
		List<String> setCookies = headersMap.get(SetCookieHeader.NAME);
		assertThat(setCookies.contains(LEGACY_COOKIE));
	}

	@Test
	public void testFailedLoginToLegacyIAM() throws Exception {
		MigrationSsoFilter filter = new MigrationSsoFilter();
		Request request = newRequest();
		when(handler.handle(context, request)).then(invocation -> {
			final Response response = new Response(Status.UNAUTHORIZED);
			return newResultPromise(response);
		});

		Response response = filter.filter(context, request, handler).get();
		assertThat(response.getStatus().equals(Status.UNAUTHORIZED));
		assertThat(response.getHeaders().get(SetCookieHeader.NAME)).isNull();
	}

	private Request newRequest() throws Exception {
		Request request = new Request();
		request.setUri("http://openig.forgerock.org");
		return request;
	}
}
