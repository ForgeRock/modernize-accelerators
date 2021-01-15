import java.net.InetAddress;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

import org.forgerock.json.JsonValue;

import com.netegrity.sdk.apiutil.SmApiConnection;
import com.netegrity.sdk.apiutil.SmApiException;
import com.netegrity.sdk.apiutil.SmApiResult;
import com.netegrity.sdk.apiutil.SmApiSession;
import com.netegrity.sdk.dmsapi.SmDmsApi;
import com.netegrity.sdk.dmsapi.SmDmsApiImpl;
import com.netegrity.sdk.dmsapi.SmDmsConfig;
import com.netegrity.sdk.dmsapi.SmDmsDirectory;
import com.netegrity.sdk.dmsapi.SmDmsDirectoryContext;
import com.netegrity.sdk.dmsapi.SmDmsObject;
import com.netegrity.sdk.dmsapi.SmDmsOrganization;
import com.netegrity.sdk.dmsapi.SmDmsSearch;
import com.netegrity.sdk.policyapi.SmObject;
import com.netegrity.sdk.policyapi.SmPolicyApi;
import com.netegrity.sdk.policyapi.SmPolicyApiImpl;
import com.netegrity.sdk.policyapi.SmUserDirectory;

import netegrity.siteminder.javaagent.AgentAPI;
import netegrity.siteminder.javaagent.InitDef;
import netegrity.siteminder.javaagent.ServerDef;

public class DmsApiSample {

	// Connection to the policy server
	public static SmApiConnection apiConnection = null;

	// SiteMinder admin credentials
	public static String ADMIN = "siteminder";
	public static String ADMIN_PWD = "Kapstone@123";

	// Directory information
	public static String USER_DIR = "ad";
	public static String LDAP_USER_SEARCH_ATTR = "samaccountname";
	public static String LDAP_USER_SEARCH_OBJECT_CLASS = "user";
	public static final String USER = "kp-test";
	public static String DIR_ROOT = "dc=kapstone,dc=com";

	@SuppressWarnings("rawtypes")
	public static void main(String[] args) throws SmApiException {
		System.out.println("Initializing AgentAPI");

		String agentName = "iisagent";
		String agentSecret = "Passw0rd";

		AgentAPI agentapi = new AgentAPI();
		ServerDef serverdef = new ServerDef();
		serverdef.serverIpAddress = "96.67.149.166";
		serverdef.connectionMin = 1;
		serverdef.connectionMax = 3;
		serverdef.connectionStep = 1;
		serverdef.timeout = 60;
		serverdef.authenticationPort = 44442;
		serverdef.authorizationPort = 44443;
		serverdef.accountingPort = 44441;
		InitDef initdef = null;
		initdef = new InitDef(agentName, agentSecret, false, serverdef);

		int retcode = agentapi.init(initdef);

		if (retcode != AgentAPI.SUCCESS) {
			System.out.println("CANNOT Connect: " + retcode);
		} else {
			System.out.println("AGENT API INIT SUCCESS");
		}

		apiConnection = new SmApiConnection(agentapi);

		SmApiSession apiSession = new SmApiSession(apiConnection);
		boolean loginResult = adminLogin(apiSession);

		System.out.println("loginResult: " + loginResult);

		// Get a list of user directories the admin can manage.
		SmPolicyApi policyApi = new SmPolicyApiImpl(apiSession);
		Vector userDirs = new Vector();

		// Returns a list of directory names.
		SmApiResult result = policyApi.getAdminUserDirs(ADMIN, userDirs);
//		printObject(userDirs, result);

		// Check if the USER_DIR can be found in the list.
		SmUserDirectory userDir = null;

		for (int i = 0; i < userDirs.size(); ++i) {
			String dir = (String) userDirs.get(i);

			if (dir.equals(USER_DIR)) {
				userDir = new SmUserDirectory(USER_DIR);
				result = policyApi.getUserDirectory(USER_DIR, userDir);
//				printObject(userDir, result);
			}
		}

		SmDmsApi dmsApi = new SmDmsApiImpl(apiSession);
		SmDmsDirectoryContext dirContext = new SmDmsDirectoryContext();
		result = dmsApi.getDirectoryContext(userDir, new SmDmsConfig(), dirContext);

		SmDmsDirectory dmsDirectory = dirContext.getDmsDirectory();
		SmDmsOrganization dmsOrg = dmsDirectory.newOrganization(DIR_ROOT);
		String dmsSearch = "(&(objectclass=" + LDAP_USER_SEARCH_OBJECT_CLASS + ") (" + LDAP_USER_SEARCH_ATTR + "="
				+ USER + "))";

		SmDmsSearch search = new SmDmsSearch(dmsSearch, DIR_ROOT);

		// Define search parameters
		search.setScope(2);// Number of levels to search.
		search.setNextItem(0);// Initialize forward search start
		search.setMaxItems(1);// Max number of items to display
		search.setPreviousItem(0);// Initialize back search start
		search.setMaxResults(1);// Max items in the result set
		result = dmsOrg.search(search, 1);
		Vector vsearch = search.getResults();
		vsearch.removeElementAt(0);
		System.out.println("vsearch.size(): " + vsearch.size());
//		for (int i = 0; i < vsearch.size(); i++) {
			SmDmsObject dmsObj = (SmDmsObject) vsearch.elementAt(0);
			System.out.println("\n***Search Result**** " + dmsObj);
			printObject(dmsObj, result);
//		}
	}

	public static boolean adminLogin(SmApiSession apiSession) {
		// SiteMinder admin login.
		try {
			InetAddress address = InetAddress.getLocalHost();

			SmApiResult result = apiSession.login(ADMIN, ADMIN_PWD, address, 0);

			if (!result.isSuccess()) {
//				printObject(null, result);
				return false;
			}
		} catch (java.net.UnknownHostException uhe) {
			System.out.println("Exception: " + uhe);
			return false;
		} catch (SmApiException apiException) {
			System.out.println("Exception: " + apiException);
			return false;
		}

		return true;
	}

	@SuppressWarnings("rawtypes")
	private static void printObject(Object obj, final SmApiResult result) {
		if (!result.isSuccess()) {
			System.out.println("STATUS_NOK");
		} else {
			System.out.println("STATUS_OK");
		}

		Map<String, String> attributesMap = new HashMap<>();
		Map<String, String> migrationAttributesMap = new HashMap<>();
		migrationAttributesMap.put("sAMAccountName", "givenName");
		migrationAttributesMap.put("mail", "email");

		if (obj != null) {
			if (obj instanceof com.netegrity.sdk.policyapi.SmObject) {
				SmObject SmObj = (SmObject) obj;
				Hashtable properties = new Hashtable(25);
				SmObj.writeProperties(properties);
				obj = properties;
			} else if (obj instanceof com.netegrity.sdk.dmsapi.SmDmsObject) {
				SmDmsObject dmsObj = (SmDmsObject) obj;
				obj = dmsObj.getAttributes();
			}

			if (obj instanceof java.util.Hashtable) {
				Enumeration ekeys = ((Hashtable) obj).keys();
				Enumeration evalues = ((Hashtable) obj).elements();

				while (evalues.hasMoreElements()) {
					String key = ekeys.nextElement().toString();
					String value = evalues.nextElement().toString();
					System.out.println(key + "=" + value);
					if (migrationAttributesMap.containsKey(key)) {
					attributesMap.put(migrationAttributesMap.get(key), value);
					}
				}
			} else if (obj instanceof java.util.Vector) {
				Enumeration evalues = ((Vector) obj).elements();

				while (evalues.hasMoreElements()) {
					System.out.println(evalues.nextElement().toString());
				}
			}
		}
		System.out.println("attributesMap: " + attributesMap);
		JsonValue value = JsonValue.json(JsonValue.object());
		for (Map.Entry<String, String> entry : attributesMap.entrySet()) {
			String key = entry.getKey();
			Object val = entry.getValue();
			value.add(key, val);
		}
		System.out.println("value: " + value);
	}

}