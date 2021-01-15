/*
** Copyright (c) 2009 CA.  All rights reserved.
** This software may not be duplicated, disclosed or reproduced in whole or
** in part for any purpose except as authorized by the applicable license agreement,
** without the express written authorization of CA. All authorized reproductions
** must be marked with this language.
**
** TO THE EXTENT PERMITTED BY APPLICABLE LAW, CA PROVIDES THIS
** SOFTWARE "AS IS" WITHOUT WARRANTY OF ANY KIND, INCLUDING
** WITHOUT LIMITATION, ANY IMPLIED WARRANTIES OF MERCHANTABILITY,
** FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.  IN NO EVENT
** WILL CA BE LIABLE TO THE END USER OR ANY THIRD PARTY FOR ANY LOSS
** OR DAMAGE, DIRECT OR INDIRECT, FROM THE USE OF THIS MATERIAL,
** INCLUDING WITHOUT LIMITATION, LOST PROFITS, BUSINESS
** INTERRUPTION, GOODWILL, OR LOST DATA, EVEN IF CA IS EXPRESSLY
** ADVISED OF SUCH LOSS OR DAMAGE.
*/

/*
 *
 *              SiteMinder Agent API Sample.
 *
 * Usage: java JavaTestClient [-l]
 *
 *        -l    Logs the output to a file
 *
 * (see the classpath options in the supplied
 * java-run.bat and java-run.sh scripts)
 */

import java.util.Enumeration;

import netegrity.siteminder.javaagent.AgentAPI;
import netegrity.siteminder.javaagent.AgentInstanceDef;
import netegrity.siteminder.javaagent.Attribute;
import netegrity.siteminder.javaagent.AttributeList;
import netegrity.siteminder.javaagent.InitDef;
import netegrity.siteminder.javaagent.RealmDef;
import netegrity.siteminder.javaagent.ResourceContextDef;
import netegrity.siteminder.javaagent.SessionDef;
import netegrity.siteminder.javaagent.TokenDescriptor;
import netegrity.siteminder.javaagent.UserCredentials;

public class JavaTestClient5x {
	public static void main(String[] args) {

		System.out.println("Initializing AgentAPI");

		String agentIP = "96.67.149.166";
		String agentName = "newiisagent";

		AgentAPI agentapi = new AgentAPI();

		InitDef initdef = new InitDef();
		int retcode = agentapi.getConfig(initdef, agentName, "/home/forgerock/Desktop/SmHost.conf");
//		System.out.println("gentapi.toString(): " + agentapi.toString());

		if (retcode != AgentAPI.SUCCESS) {
			System.out.println("Config returned error: " + retcode);
		} else {
			System.out.println("Init Agent API Config success: " + retcode);
		}
		retcode = agentapi.init(initdef);

		if (retcode != AgentAPI.SUCCESS) {
			System.out.println("AGENT API INIT FAILED: " + retcode);
		} else {
			System.out.println("AGENT API INIT SUCCESS: " + retcode);
		}
		
		
		
		
		
		
//        AgentInstanceDef agentInstanceDef = new AgentInstanceDef("SampleSDK",
//                "R12.5",
//                "SDK Agent",
//                "windows",
//                "AgentGUID.conf",
//                "ACO-SampleSDK",
//                "HCO-SampleSDK",
//                "COMPAT");
//		
//		
//        retcode = agentapi.setAgentInstanceInfo(agentInstanceDef);
//        System.out.println("setAgentInstanceInfo retcode: " + retcode);
		

		AttributeList attrList = new AttributeList();

		String resource = "/sales";
		ResourceContextDef resctxdef = new ResourceContextDef(agentName, "", resource, "GET");
		RealmDef realmdef = new RealmDef();
		retcode = agentapi.isProtected(agentIP, resctxdef, realmdef);

		System.out.println("isProtected retcode: " + retcode);

		UserCredentials usercreds = new UserCredentials("kp-test", "Kapstone@123");

		SessionDef sessionDef = new SessionDef();
		attrList = new AttributeList();

		retcode = agentapi.login(agentIP, resctxdef, realmdef, usercreds, sessionDef, attrList);

		System.out.println("retcode: " + retcode);
		System.out.println("Session Id: " + sessionDef.id);
		System.out.println("Session Spec: " + sessionDef.spec);
//		displayAttributes(attrList);

		String transID = "TranCode1";
		retcode = agentapi.authorize("96.67.149.166", transID, resctxdef, realmdef, sessionDef, attrList);
		System.out.println("retcodenew: " + retcode);
		displayAttributes(attrList);

		// WORKING SO FAR

		Enumeration attrListEnum = attrList.attributes();
		byte[] bDNval = { 0 };

		while (attrListEnum.hasMoreElements()) {
			Attribute attr = (Attribute) attrListEnum.nextElement();

			if (attr.id == AgentAPI.ATTR_USERDN) {
				bDNval = attr.value;
			}
		}

		// create attribute list for creating an SSO token
		AttributeList ssoAttrs = new AttributeList();

		// add the username attribute to the list
		byte[] bUNval = "kp-test".getBytes();
		ssoAttrs.addAttribute(AgentAPI.ATTR_USERNAME, 0, 0, "", bUNval);

		// add the user dn attribute to the list
		ssoAttrs.addAttribute(AgentAPI.ATTR_USERDN, 0, 0, "", bDNval);

		// add the IP address attribute to the list
		byte[] bIPval = agentIP.getBytes();
		ssoAttrs.addAttribute(AgentAPI.ATTR_CLIENTIP, 0, 0, "", bIPval);

		// this object will recieve the token
		StringBuffer ssoToken = new StringBuffer();

		retcode = agentapi.createSSOToken(sessionDef, ssoAttrs, ssoToken);
		System.out.println("retcode SSO: " + retcode);
		System.out.println("SSOToken: " + ssoToken.toString());

		// create attribute list to receive attributes from the SSO token
		AttributeList ssoRespAttrs = new AttributeList();
		TokenDescriptor tokendesc = new TokenDescriptor(0, false);

		// request that an updated token be produced
		boolean updateToken = true;

		// this object will recieve the updated token
		StringBuffer updatedSSOToken = new StringBuffer();

		retcode = agentapi.decodeSSOToken(ssoToken.toString(), tokendesc, ssoRespAttrs, updateToken, updatedSSOToken);

		System.out.println("retcode SSO token: " + retcode);
		System.out.println("SSO token attributes: ");
		displayAttributes(ssoRespAttrs);

		agentapi.unInit();
	}

	@SuppressWarnings("rawtypes")
	private static void displayAttributes(AttributeList attributeList) {
		Enumeration enumer = attributeList.attributes();

		if (!enumer.hasMoreElements()) {
			System.out.println("No attributes found");
		}

		while (enumer.hasMoreElements()) {
			Attribute attr = (Attribute) enumer.nextElement();

			System.out.println(attr.id + "\t" + new String(attr.value));
		}
	}
}