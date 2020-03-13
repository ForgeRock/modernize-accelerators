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
