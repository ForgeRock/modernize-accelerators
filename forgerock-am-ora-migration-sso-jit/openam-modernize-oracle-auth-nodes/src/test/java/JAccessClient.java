import java.net.URISyntaxException;
import java.util.Hashtable;

import org.forgerock.http.Client;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.HttpClientHandler;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.util.promise.NeverThrowsException;

import oracle.security.am.asdk.AccessClient;
import oracle.security.am.asdk.AccessException;
import oracle.security.am.asdk.AuthenticationScheme;
import oracle.security.am.asdk.ResourceRequest;
import oracle.security.am.asdk.UserSession;

public class JAccessClient {
	public static final String ms_resource = "//ec2-107-23-96-14.compute-1.amazonaws.com:14100/oam/server/obrareq.cgi";
	public static final String ms_protocol = "http";
	public static final String ms_method = "GET";
	public static final String ms_login = "oamuser";// oamadmin, demo, user.1
	public static final String ms_passwd = "Passw0rd123";
	public static final String m_configLocation = "./config";

	public static void main(String argv[]) {
		AccessClient ac = null;
		try {
			System.out.println("Start");
			ac = AccessClient.createDefaultInstance(m_configLocation, AccessClient.CompatibilityMode.OAM_10G);
			System.out.println("End");
			ResourceRequest rrq = new ResourceRequest(ms_protocol, ms_resource, ms_method);
			System.out.println("End2");
			if (rrq.isProtected()) {
				System.out.println("Resource is protected.");
				AuthenticationScheme authnScheme = new AuthenticationScheme(rrq);
				if (authnScheme.isForm()) {
					System.out.println("Form Authentication Scheme.");
					Hashtable<String, String> creds = new Hashtable<String, String>();
					creds.put("userid", ms_login);
					creds.put("password", ms_passwd);
					UserSession session = new UserSession(rrq, creds);
					if (session.getStatus() == UserSession.LOGGEDIN) {
						if (session.isAuthorized(rrq)) {
							String responseCookie = session.getSessionToken();
							System.out.println("Session: " + responseCookie);
						} else {
						}
					} else {
					}
					// user can be logged out by calling log off method on the session object
				} else {
					System.out.println("User is NOT logged in");
				}
			} else {
				System.out.println("non-Form Authentication Scheme.");
			}
		} catch (AccessException ae) {
			System.out.println("Access Exception: " + ae.getMessage());
			ae.printStackTrace();
		}
		ac.shutdown();
	}

	private static void getProfileDetails(String ssoToken) {
		String endpoint = "http://ec2-107-23-96-14.compute-1.amazonaws.com:14100/oic_rest/rest/userprofile/people/demo";

		Request request = new Request();
		try {
			request.setMethod("GET").setUri(endpoint);
			request.getHeaders().add("Content-Type", "application/json");
		} catch (URISyntaxException e) {
		}

		HttpClientHandler httpClientHandler = null;
		try {
			httpClientHandler = new HttpClientHandler();
		} catch (HttpApplicationException e) {
			e.printStackTrace();
		}
		Response response = null;
		Client client = new Client(httpClientHandler);
		try {
			response = client.send(request).getOrThrow();
		} catch (NeverThrowsException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		System.out.println(response.getEntity());
	}

}