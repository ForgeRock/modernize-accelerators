# Disclaimer
The sample code described herein is provided on an "as is" basis, without warranty of any kind, to the fullest extent permitted by law. ForgeRock does not warrant or guarantee the individual success developers may have in implementing the sample code on their development platforms or in production configurations.
<br><br>
ForgeRock does not warrant, guarantee or make any representations regarding the use, results of use, accuracy, timeliness or completeness of any data or information relating to the sample code. ForgeRock disclaims all warranties, expressed or implied, and in particular, disclaims all warranties of merchantability, and warranties related to the code, or any service or software related thereto.
<br><br>
ForgeRock shall not be liable for any direct, indirect or consequential damages or costs of any type arising out of any action taken by you or others related to the sample code.

# Modernize IAM Accelerators - IG Based Bi-Directional SSO and JIT Toolkit
With deployments of tens or hundreds of legacy applications, migration waves may be required to minimize the operational impact on production systems. With this type of use case, coexistence and SSO between legacy IAM and ForgeRock IAM is often needed.

## 1. Contents
The toolkit provides a collection of resources that can handle very complex migration scenarios, including bidirectional SSO between legacy IAM and ForgeRock AM.
The framework can be easily extended to support migrations from any legacy IAM platform that is capable of exposing client SDKs/APIs for operations such as:
    - Validating existing legacy IAM tokens
    - Using an authentication API (with a username and password input)

### 1.1. Assets Included
Bidirectional SSO capability between legacy IAM and ForgeRock IAM helps minimize risk and time to market in complex migration projects.
ForgeRock understands customers' needs to speed up migration design decisions and cut implementation time, and is thus delivering the following assets as part of the Migration Accelerators:


System                           | Type                | Name                               | Description
---------------------------------|---------------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------
Interface                        | Java class          | LegacyIAMProvider.java             | the main interface provided for implementing the migration components
Interface implementation example | Java class          | LegacyOpenSSOProvider.java         | Example implementation of the main interface
IG                               | Route               | migration-assets-authentication    | A route that covers the authentication endpoint


## 2. Building The Source Code

+ <b>Important note:</b> The assets presented below are built based over OpenIG version 7.0.1.

In order to build the project from the command line, follow the steps presented below. Make sure that you have all the prerequisites installed correctly before starting.

### 2.1. Prerequisites - Prepare Your Environment

You will need the following software to build the code:

Software               | Required Version
---------------------- | ----------------
Java Development Kit   | 11.0 and above
Maven                  | 3.1.0 and above
Git                    | 1.7.6 and above

The following environment variables should be set:

- `JAVA_HOME` - points to the location of the version of Java that Maven will use.
- `M2_HOME` - points to the location of the Maven installation and settings.
- `MAVEN_OPTS` - sets some options for the JVM when running Maven.

For example, your environment variables should look similar to this:

```
JAVA_HOME=/usr/jdk/jdk-11.0.9
MAVEN_HOME=/opt/apache-maven-3.6.3
MAVEN_OPTS='-Xmx2g -Xms2g -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512m'
```

### 2.2. Getting the repository

If you want to get the assets contained in this package, you must start by cloning the ForgeRock repository:

```
mkdir demo
git clone https://github.com/ForgeRock/modernize-accelerators.git
cd forgerock-ig-migration-sso-jit
```

### 2.3. Resources explained

#### 2.3.1. The main interface

+ LegacyIAMProvider contains the 3 base methods that must be implemented:
    + getUserCredentials - Implementation must read the user credentials from the Forgerock HTTP Request. The HTTP request gives flexibility to capture the user's credentials from the request headers or from the request body. Should output a User object with the intercepted username and password.
    + getExtendedUserAttributes - Get user profile attributes from the legacy IAM, with userName as input.
    + validateLegacyAuthResponse - Validate if the authentication response from the legacy system is successfull.


#### 2.3.2. The interface implementation example

The LegacyOpenSSOProvider is an example class that demonstrated the usage and implementation of the main interface described in section 2.3.1. 
We can see that the 3 main methods of the interface are implemented here, along with other methods that do specific actions for the user's IAM platform.

This project uses a config file for any configurable properties you might need. The <b>LegacyOpenSSOProvider.properties</b> file is located under /src/main/resources/org/forgerock/openig/modernize/impl.

#### 2.3.3. The IG filters

The [openig-modernize-filters](https://github.com/ForgeRock/modernize-accelerators/tree/develop/forgerock-ig-migration-sso-jit/openig-modernize-filters) project brings together all the resources presented so far, and leads the sequence of actions required for SSO and JIT. This is orchestrated by the filter <b>MigrationSsoFilter</b>.

### 2.4. Building the Code

The build process and dependencies are managed by Maven. The first time you build the project, Maven will pull down all the dependencies and Maven plugins required by the build, which can take a longer time. 
Subsequent builds will be much faster!

```
cd modernize-accelerators/forgerock-ig-migration-sso-jit/openig-modernize-auth-nodes
mvn package
```

Maven builds the binary in `openig-modernize-filters/target/`. The file name format is `openig-modernize-filters-<nextversion>-SNAPSHOT.jar` . 
For example, "openig-modernize-filters-1.0-SNAPSHOT.jar".


### 2.5. Building the OpenIG war file

+ Download and unzip the IG.war file from ForgeRock BackStage:

```
https://backstage.forgerock.com/downloads/browse/ig/latest
mkdir ig && cd ig
jar -xf ~/Downloads/IG-7.0.1.war
```

+ Copy the jar file containing the IG filters: `cp ~/openig-modernize-filters-<nextversion>-SNAPSHOT.jar WEB-INF/lib` in the /ig/WEB-INF/lib folder.

+ Rebuild the WAR file: 

```
jar -cf ../ig.war *
```

+ Copy and deploy the ig.war file in the container in which IG is deployed.

## 3. Configuration

### 3.1. Secrets
If you have any passwords that you need in the service to service context, they must be saved in a secret store for security reasons.

The section below shows an example for a FileSystemSecretStore configuration.

```
"secrets": {
    "stores": [
      {
        "type": "FileSystemSecretStore",
        "config": {
          "format": "PLAIN",
          "directory": "${openig.baseDirectory.path}/secrets"
        }
      }
    ]
  }
```

On the IG instance filesystem, create the directory path configured in the directory field and add the needed secrets. 

### 3.2. Routes

The route provided with this toolkit serves as an example of implementation. The route requires specific adaptation to each user's case and should not be used as packaged here.

+ [migration-assets-authentication-route](https://github.com/ForgeRock/modernize-accelerators/blob/develop/forgerock-ig-migration-sso-jit/openig-modernize-routes/migration-assets-authentication-route.json)
<br>Route filters:

- <b>MigrationSsoFilter</b> - Custom filter provided in this SSO toolkit. The filter does the following actions:
    - Intercepts the user' credentials from the authentication request by calling the method <b>getUserCredentials</b>.
	- Verifies if the user is migrated in ForgeRock IDM
		- If the user is migrated:
			- he is authenticated in ForgeRock AM
			- the request is passed through to the legacy IAM and the user is authenticated there also
			- when legacy IAM responds, the user will have on the HTTP response a Set-Cookie header representing the legacy SSO token. The filter also adds a Set-Cookie header with the value of the SSO token resulted after authentication to ForgeRock AM.
			- As a result, the user will have in his browser two tokens, one for the legacy IAM, and one for the ForgeRock AM.
			
		- If the user is not migrated:
			- the filter allows the request to pass directly to legacy IAM to validate the credentials
			- on the response from legacy IAM, the filter verifies if the authentication succeeded by calling the  method <b>validateLegacyAuthResponse</b>.
			- on successful authentication in legacy IAM, the filter attempts to retrieve the user profile details by calling the method <b>getExtendedUserAttributes</b>.
			- with the user profile attributes retrieved, the filter provisions the user in ForgeRock IDM.

- Filter config example:
```
{
        "name" : "MigrationSsoFilter",
        "type" : "MigrationSsoFilter",
        "config" : {
          "userAttributesMapping" : {
            "username" : "userName",
            "givenName" : "givenName",
            "cn" : "cn",
            "sn" : "sn",
            "mail" : "mail"
          },
          "getUserMigrationStatusEndpoint" : "https://openidm.example.com/openidm/managed/user",
          "provisionUserEndpoint" : "https://openidm.example.com/openidm/managed/user?_action=create",
          "openaAmAuthenticateURL" : "https://openam.example.com/openam/json/{{realm}}/authenticate",
          "openAmCookieName" : "iPlanetDirectoryPro",
          "acceptApiVersionHeader" : "Accept-API-Version",
          "acceptApiVersionHeaderValue" : "resource=2.0, protocol=1.0",
          "setCookieHeader" : "Set-Cookie"
        }
      }
```

```
Filter Class: /openig-modernize-filters/src/main/java/org/forgerock/openig/modernize/filter/MigrationSsoFilter.java
```

| Configuration                      | Example                                                                        | Description
| ---------------------------------- | ------------------------------------------------------------------------------ | --------------------------------------------------------------------------- |
| getUserMigrationStatusEndpoint     | https://openidm.example.com/openidm/managed/user                               | This represents the user endpoint from ForgeRock IDM, with a query action.  |
| provisionUserEndpoint              | https://openidm.example.com/openidm/managed/user?_action=create                | This represents the user endpoint from ForgeRock IDM, with a create action. |
| openaAmAuthenticateURL             | https://openam.example.com/openam/json/{{realm}}/authenticate                  | The ForgeRock OpenAM authenticate endpoint. |
| openAmCookieName                   | iPlanetDirectoryPro                                                            | The ForgeRock OpenAM cookie name. |
| acceptApiVersionHeader             | Accept-API-Version                                                             | The Accept-API-Version header name |
| acceptApiVersionHeaderValue        | resource=2.0, protocol=1.0                                                     | The Accept-API-Version version used. |
| setCookieHeader                    | Set-Cookie                                                                     | The Set-Cookie header name. |


<br>

- <b>HeaderFilter-ChangeHostFilter</b> - Out of the box filter that comes with the IG application. This filter is used to remove and add new headers on the HTTP request or response.

<br>

- <b>ClientCredentialsOAuth2ClientFilter</b> - Authenticates OAuth 2.0 clients by using the client's OAuth 2.0 credentials to obtain an access_token from an authorization server, and injecting the access_token into the inbound request as a Bearer Authorization header. For this toolkit it's used to obtain the token needed by the MigrationSsoFilter to call IDM to check if a user is migrated, or to create a new user.

<br>

## 4. Extending & Customizing
Any changes you need to make to adapt to a specific legacy system can be done in the provided sample project. To do so, you first need to import the project you downloaded - https://github.com/ForgeRock/modernize-accelerators/tree/develop/forgerock-ig-migration-sso-jit from GitHub.

## 5. Troubleshooting Common Problems
+ N/A

## 6. Known issues
+ N/A

## 7. License

This project is licensed under the Apache License, Version 2.0. The following text applies to both this file, and should also be included in all files in the project:

```
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
```
