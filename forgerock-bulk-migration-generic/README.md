# Disclaimer
The sample code described herein is provided on an "as is" basis, without warranty of any kind, to the fullest extent permitted by law. ForgeRock does not warrant or guarantee the individual success developers may have in implementing the sample code on their development platforms or in production configurations.
<br><br>
ForgeRock does not warrant, guarantee or make any representations regarding the use, results of use, accuracy, timeliness or completeness of any data or information relating to the sample code. ForgeRock disclaims all warranties, expressed or implied, and in particular, disclaims all warranties of merchantability, and warranties related to the code, or any service or software related thereto.
<br><br>
ForgeRock shall not be liable for any direct, indirect or consequential damages or costs of any type arising out of any action taken by you or others related to the sample code.

# Modernize Accelerators - Bulk Migration Generic Toolkit (with IDM and DS)
One-time and incremental import of user profiles from Legacy LDAPv3 store to Forgerock DS is often a requirement in complex migration process.
With custom schema being used by Legacy IAM systems (custom object classes, custom attributes, custom naming and organization units/suffix) the process of synchronizing information can be complex to design and implement. In addition, the particular mapping of the extended schema (attributes, object classes and group-membership used for core IAM transactions) can also be cumbersome and lengthy.

## 1. Contents
The following assets have been included in the Migration Accelerators for this purpose:
	- Template for LDAPv3 to LDAPv3 user reconciliation from Legacy IAM to Forgerock DS
	- Mapping for common identity information: uid, common name, group-membership, status, mail, last login, account locked features, number of wrong attempts (tentative)

### 1.1. Assets Included
This toolkit implements one-way synchronization from an external Legacy IAM userstore to the Forgerock IDM repository and then synchronization to Forgerock Directory Server as the next generation userstore.
	- The toolkit can be extended to work with any compliant source connector. User objects in the source system file are synchronized with the managed users in the Forgerock IDM repository and then are pushed in Forgerock DS based on the provided mappings.
	- Both inbound mappings and outbound mappings can be extended for the specific customer scenarios.
	- The sample source connector is LDAPv3 but may be adapter in the customer context.

```
System         | Type                | Name                               | Description
---------------|---------------------|------------------------------------|--------------------------------------------------------------------------------------------------------
IDM		       | Managed Object      | managed.json						  | Enhanced user object definition that brings several other typical attributes in the IDM definition.
IDM	           | Mapping             | sync.json			              | Source mapping set for Legacy IAM to IDM managed object.
IDM            | Mapping             | sync.json				          | Source mapping set for IDM managed object to Forgerock DS.
IDM            | Connector           | provisioner.openicf-legacyIAM.json | Source connector that pulls user identities from Legacy IAM (LDAPv3 connector)
IDM            | Connector           | provisioner.openicf-FRDS.json      | Target connector that pushes identity information inside Forgerock Directory Server (LDAPv3 connector)
```

## 2. Getting the repository

If you want to get the assets contained in this package, you must start by cloning the ForgeRock repository:

```
mkdir demo
git clone https://github.com/ForgeRock/modernize-accelerators.git
cd forgerock-bulk-migration-generic
```

## 3. Configuration

### 3.1. JSON config files

The route provided with this toolkit serves as an example of implementation. The route requires specific adaptation to each user's case and should not be used as packaged here.

+ [migration-assets-authentication-route](https://github.com/ForgeRock/modernize-accelerators/blob/develop/forgerock-ig-migration-sso-jit/openig-modernize-routes/migration-assets-authentication-route.json)
<br>Route filters:

- <b>MigrationSsoFilter</b> - Custom filter provided in this SSO toolkit. The filter does the following actions:
    - Intercepts the user' creddentials from the authentication request by calling the framework method implementation <b>getUserCredentials</b> injected via java reflection API.
	- Verifies if the user is migrated in ForgeRock IDM
		- If the user is migrated:
			- he is authenticated in ForgeRock AM
			- the request is passed through to the legacy IAM and the user is authenticated there also
			- when legacy IAM responds, the use will have on the HTTP response a Set-Cookie header representing the legacy SSO token. The filter also adds a Set-Cookie header with the value of the SSO token resulted after authentication to ForgeRock AM.
			- As a result, the user will have in his browser two tokens, on for the legacy IAM, and one for the ForgeRock AM.
			
		- If the user is not migrated:
			- the filter allows the request to pass directly to legacy IAM to validate the credentials
			- on the response from legacy IAM, the filter verifies if the authentication succeeded by calling the framework method implementation <b>validateLegacyAuthResponse</b> injected via java reflection API.
			- on successfull authentication in legacy IAM, the filter attempts to retrieve the user profile details by calling the framework method implementation <b>getExtendedUserAttributes</b> injected via java reflection API.
			- with the user profile attributes retrieved, the filter provisions the user in ForgeRock IDM.
```
Node Class: /src/main/java/org.forgerock.openam.auth.node.CheckLegacyToken.java
Plugin class: /src/main/java/org.forgerock.openam.auth.node.plugin.CheckLegacyTokenPlugin.java
Configuration File: /src/main/resources/org/forgerock/openam/auth/node/CheckLegacyToken.properties

Configuration          | Example                                                            | Description
---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------
Legacy Token Endpoint  | <<proto>>://<<host>>/openam/json/sessions?tokenId=                 | field for the end point used by the Legacy iAM to verify if an SSO token is valid
Legacy cookie name     | iPlanetDirectoryPro                                                | field for the name of the SSO token expected by the legacy token verification end point.
```

<br>

- <b>HeaderFilter-ChangeHostFilter</b> -Out of the box filter that comes with the IG application. This filter is used to remove and new headers on the HTTP request or response.


## 4. Extending & Customizing
TBD

## 5. Troubleshooting Common Problems
TBD

## 6. Known issues
+ N/A

## 7. License

This project is licensed under the Apache License, Version 2.0. The following text applies to both this file, and should also be included in all files in the project:

```
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
```