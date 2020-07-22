# Disclaimer
The sample code described herein is provided on an "as is" basis, without warranty of any kind, to the fullest extent permitted by law. ForgeRock does not warrant or guarantee the individual success developers may have in implementing the sample code on their development platforms or in production configurations.
<br><br>
ForgeRock does not warrant, guarantee or make any representations regarding the use, results of use, accuracy, timeliness or completeness of any data or information relating to the sample code. ForgeRock disclaims all warranties, expressed or implied, and in particular, disclaims all warranties of merchantability, and warranties related to the code, or any service or software related thereto.
<br><br>
ForgeRock shall not be liable for any direct, indirect or consequential damages or costs of any type arising out of any action taken by you or others related to the sample code.

# Modernize Accelerators - SSO Toolkit (with AM)
With deployments of tens or hundreds of legacy applications, migration waves may be required to minimize the operational impact on production systems. With this type of use case, coexistence and SSO between legacy IAM and ForgeRock IAM is often needed.
Sometimes putting IG in front of a legacy system is not an option for commercial reasons. 

## 1. Contents
The toolkit provides a collection of custom nodes and a migration tree that can handle very complex migration scenarios, including bidirectional SSO between legacy IAM and ForgeRock AM.
The framework can be easily extended to support migrations from any legacy IAM platform that is capable of exposing client SDKs/APIs for operations such as:
    - Validating existing legacy IAM tokens
    - Using an authentication API (with a username and password input)

### 1.1. Assets Included
Bidirectional SSO capability between legacy IAM and ForgeRock IAM helps minimize risk and time to market in complex migration projects.
ForgeRock understands customers' needs to speed up migration design decisions and cut implementation time, and is thus delivering the following assets as part of the Migration Accelerators:
- A collection of custom migration authentication nodes (ValidateLegacyToken, GenerateLegacyToken, RetrieveLegacyProfil)
- A prebuilt migration authentication tree with bidirectional SSO support that embeds custom nodes and migration know-how (including handling invalid authentication attempts)
- Password synchronization capabilities inside the authentication tree
- A flex option allowing the extension of the authentication tree and nodes for a specific vendor legacy IAM system

```
System  | Type                | Name                             | Description
--------| --------------------|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------
AM      | Node                | Legacy-FR-Validate Token         | Retrieves a token from an existing cookie, validates the token against legacy IAM, and provides as output in the shared state the username and outcome
AM      | Node                | Legacy-FR-Migration Status       | Searches the user identity in ForgeRock IDM based on the username from the shared state
AM      | Node                | Legacy-FR-Create FR User         | Calls the ForgeRock IDM API to provision the managed user
AM      | Node                | Legacy-FR-Login                  | Based on the username and password from the shared state, executes the legacy IAM login API call
AM      | Node                | Legacy-FR-Set Password           | Updates the ForgeRock IDM managed user object with the password captured and stored in the shared state
AM      | Tree Hook           | LegacySessionTreeHook            | Manages cookies if a successfull login is performed into legacy IAM by the tree
AM      | Authentication Tree | migrationTree                    | Implements the migration login and bidirectional SSO
AM      | Custom Nodes        | migration-am-custom-SNAPSHOT.jar | Custom AM nodes used in the migration authentication tree
```

## 2. Building The Source Code

+ <b>Important note:</b> The assets presented below are built based on OpenAM version 6.5.2.

In order to build the project from the command line, follow the steps presented below. Make sure that you have all the prerequisites installed correctly before starting.
<br>
+ <b>Demo video</b> - [downloading and building the source code](https://github.com/ForgeRock/modernize-accelerators/blob/master/forgerock-am-migration-sso-jit/video/Part1-Building_The_Code.mp4) - All the steps below can be followed in this video recording.

### 2.1. Prerequisites - Prepare your Environment

#### 2.1.1. Software and environment

You will need the following software to build the code:

```
Software               | Required Version
---------------------- | ----------------
Java Development Kit   | 1.8 and above
Maven                  | 3.1.0 and above
Git                    | 1.7.6 and above
```
The following environment variables should be set:

- `JAVA_HOME` - points to the location of the version of Java that Maven will use.
- `M2_HOME` - points to the location of the Maven installation and settings.
- `MAVEN_OPTS` - sets some options for the JVM when running Maven.

For example, your environment variables should look similar to this:

```
JAVA_HOME=/usr/jdk/jdk1.8.0_201
MAVEN_HOME=/opt/apache-maven-3.6.3
MAVEN_OPTS='-Xmx2g -Xms2g -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512m'
```

#### 2.1.2. External libraries

+ The source files use some Spring dependencies from the 5.2.1.RELEASE that you will need to download. The following JAR files must be added to the WEB-INF/lib directory of the OpenAM war, before building it as described in section 2.4. below:

+ [spring-beans-5.2.1.RELEASE](https://mvnrepository.com/artifact/org.springframework/spring-beans)
+ [spring-core-5.2.1.RELEASE](https://mvnrepository.com/artifact/org.springframework/spring-core)
+ [spring-jcl-5.2.1.RELEASE](https://mvnrepository.com/artifact/org.springframework/spring-jcl)
+ [spring-web-5.2.1.RELEASE](https://mvnrepository.com/artifact/org.springframework/spring-web)

#### 2.1.3. Reverse proxy

Usually all components are deployed under the same domain, but if your legacy IAM is under another domain than the ForgeRock applications, you will need a reverse proxy in front of both legacy and ForgeRock. This will ensure all the cookies will be seen between aplications from the same domain, otherwise SSO can't be achieved.

### 2.2. Getting the Code

If you want to run the code unmodified, you can simply clone the ForgeRock repository:

```
mkdir demo
git clone https://github.com/ForgeRock/modernize-accelerators.git
```


### 2.3. Building the Code

The build process and dependencies are managed by Maven. The first time you build the project, Maven will pull 
down all the dependencies and Maven plugins required by the build, which can take a longer time. 
Subsequent builds will be much faster!

```
cd modernize-accelerators/forgerock-am-migration-sso-jit/openam-modernize-auth-nodes
mvn package
```

Maven builds the binary in `openam-modernize-auth-nodes/target/`. The file name format is `openam-modernize-auth-nodes-<nextversion>-SNAPSHOT.jar` . 
For example, "openam-modernize-auth-nodes-1.0.0-SNAPSHOT.jar".


### 2.4. Adding the Library to the AM WAR File

+ Download and unzip the AM.war file from ForgeRock BackStage:

```
https://backstage.forgerock.com/downloads/browse/am/latest
mkdir ROOT && cd ROOT
jar -xf ~/Downloads/AM-6.5.2.2.war
```

+ Copy the newly generated JAR file to the /ROOT/WEB-INF/lib folder:

```
cp ~/openam-modernize-auth-nodes-<nextversion>-SNAPSHOT.jar WEB-INF/lib
```

+ Rebuild the WAR file: 

```
jar -cf ../ROOT.war *
```

+ In order for you to see the nodes included in the JAR file you built previously, you must copy and deploy the ROOT.war file on the container in which AM is deployed.

## 3. Configuration

### 3.1. Authentication Tree

Please see the ForgeRock [documentation](https://backstage.forgerock.com/docs/am/6.5/authentication-guide/index.html#sec-configure-authentication-trees) for information about how to create authentication trees.

To set your custom authentication tree as the default tree inside a realm, navigate to 'Authentication' -> 'Settings' -> 'Core'. Then select your custom authentication tree in the 'Organization Authentication Configuration' field. 


#### 3.1.1. Building the tree

+ <b>Demo video</b> - [building the tree](https://github.com/ForgeRock/modernize-accelerators/blob/master/forgerock-am-migration-sso-jit/video/Part2-Building_The_Tree.mp4) - In this recording you can watch how the tree is built step by step.
<br>

![migrationTree](images/migrationTree.png)

#### 3.1.2. Alternative - Importing the Tree with Amster

The SSO toolkit also comes with a built tree that has been exported with Amster. This tree can be imported to other AM servers. Please refer to the [documentation](https://backstage.forgerock.com/docs/amster/6.5/user-guide/#sec-usage-import) for information about how to use Amster to import resources.

The tree export and its nodes can be found in the folder: 

```
/modernize-accelerators/forgerock-am-migration-sso-jit/amster-export
```

In our example, the tree was created and exported in the root realm, but as a best practice you should never use the root realm. If you choose to import the migration tree with Amster, make sure to replace the realm property with your own value in the amster-export resources provided.


### 3.2. Tree Nodes

A node is the core abstraction within an authentication tree. Trees consist of nodes, which can modify the shared state and request input from the user via callbacks.

#### 3.2.1. Legacy-FR-Validate Token
Custom node provided in this SSO toolkit. Detects if an existing legacy token exists in the browser in a specific cookie, and validates this as an active token against the legacy IAM system via an SDK/API call. The default node uses a GET API call with the cookie fetched from the incoming http request. The name of the cookie and the target URL is configurable. The node is vendor-specific and is flexible enough to be tailored for each vendor. The Oracle plugin provides a custom implementation for this node using the Oracle Access Client SDK.

```
Node Class: /src/main/java/org/forgerock/openam/auth/node/LegacyFRValidateToken.java
Configuration File: /src/main/resources/org/forgerock/openam/auth/node/LegacyFRValidateToken.properties

Configuration          | Example                                                            | Description
---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------
Legacy Token Endpoint  | <<proto>>://<<host>>/openam/json/sessions?tokenId=                 | End point used by the legacy IAM to verify if an SSO token is valid
Legacy Cookie Name     | iPlanetDirectoryPro                                                | Name of the SSO token expected by the legacy token verification end point
```

<br>

#### 3.2.2. Legacy-FR-Migration Status
Custom node provided in the SSO toolkit. Calls the default ForgeRock IDM managed object API to determine whether the user is already migrated. This node is generic, and does not need to be customized for specific legacy IAM vendor implementations.

```
Node Class: /src/main/java/org/forgerock/openam/auth/node/LegacyFRMigrationStatus.java
Configuration File: /src/main/resources/org/forgerock/openam/auth/node/LegacyFRMigrationStatus.properties

Configuration          | Example                                                            | Description
---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------
IDM User Endpoint      | <<proto>://<<host>>/openidm/managed/user?_queryFilter=userName+eq+ | End point used to verify in ForgeRock IDM if the user is migrated
IDM Admin User         | idmAdmin                                                           | IDM admin user used to query the IDM user endpoint
IDM Password Secret ID | openidmadminpass                                                   | field for the IDM admin password secret id. The secret from the file system with this id. must contain the value of the password for the IDM administrator user
```

<br>

#### 3.2.3. Page Node
The default page node in ForgeRock IAM used to capture user credentials. This node is generic, and does not need to be customized for specific legacy IAM vendor implementations.

<br>

#### 3.2.4. Legacy-FR-Create FR User
Custom node provided in the SSO toolkit. Calls the default ForgeRock IDM managed object API to provision the user in ForgeRock. This node is generic, and does not need to be customized for specific legacy IAM vendor implementations. The node uses the following shared state attributes: 

```
Node Class: /src/main/java/org/forgerock/openam/auth/node/LegacyFRCreateForgeRockUser.java
Configuration File: /src/main/resources/org/forgerock/openam/auth/node/LegacyfrCreateForgeRockUser.properties

Configuration          | Example                                                            |Description
---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
Legacy URL             | <<proto>>://<<host>>/openam/json/realms/root/realms/legacy/users/  | End point used to get profile attributes from the legacy IAM
IDM User Endpoint      | <<proto>>://<<host>>/openidm/managed/user?_action=create           | End point used to create a user in ForgeRock IDM
IDM Admin User         | idmAdmin                                                           | IDM admin user used to query the IDM user endpoint
IDM Password Secret ID | openidmadminpass                                                   | field for the IDM admin password secret id. The secret from the file system with this id. must contain the value of the password for the IDM administrator user
Set Password Reset     | true/false - on/off                                                | Switch used to determine if the node is used on a scenario that cannot migrate the user password. Set to true if the password can't be migrated.
```

<br>

#### 3.2.5. Data Store Decision
This is the default node for credential validation in ForgeRock IAM. This node is generic, and does not need to be customized for specific legacy IAM vendor implementations.

<br>

#### 3.2.6. Legacy-FR-Login
Custom node provided in the SSO toolkit. Validates credentials (username and password) entered by the user against the legacy IAM system via an SDK/API call. The default node uses a POST API call with the username and password fetched from the shared state. The URL is configurable, the node expects a successful response of 200 OK and a specific cookie to be present in the response. The cookie name is configurable. The node is vendor-specific and is flexible enough to be tailored for each vendor. The Oracle plugin provides a custom implementation for this node using the Oracle Access Client SDK.

```
Node Class: /src/main/java/org/forgerock/openam/auth/node/LegacyFRLogin.java
Configuration File: /src/main/resources/org/forgerock/openam/auth/node/LegacyFRLogin.properties

Configuration          | Example                                                            | Description
---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------
Legacy Token Endpoint  | <<proto>>://<<host>>/json/realms/root/realms/legacy/authenticate   | End point used by the Legacy iAM to authenticate an user
Legacy Cookie Name     | iPlanetDirectoryPro                                                | Name of the SSO token returned by the legacy IAM for a successful authentication.
```

<br>

#### 3.2.7. Legacy-FR-Set Password
Custom node provided in the SSO toolkit. Calls the default ForgeRock IDM managed object API to provision the user password in ForgeRock IAM. This node is generic one, and does not need to be customized for specific legacy IAM vendor implementations.

```
Node Class: /src/main/java/org/forgerock/openam/auth/node/LegacyFRSetPassword.java
Configuration File: /src/main/resources/org/forgerock/openam/auth/node/LegacyFRSetPassword.properties

Configuration          | Example                                                                           | Description
---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------
IDM User Endpoint      | <<proto>>://<<host>>/openidm/managed/user?_action=patch&_queryFilter=userName+eq+ | End point used to create a user in ForgeRock IDM
IDM Admin User         | idmAdmin                                                                          | IDM admin user used to query the IDM user endpoint
IDM Password Secret ID | openidmadminpass                                                   | field for the IDM admin password secret id. The secret from the file system with this id. must contain the value of the password for the IDM administrator user
```

## 4. Scenarios

+ <b>Demo video</b> - [testing the main tree scenarios](https://github.com/ForgeRock/modernize-accelerators/blob/master/forgerock-am-migration-sso-jit/video/Part3-Testing_The_Tree.mp4) - This video recording demonstrates how the scenarios detailed below are triggered by using the authentication tree.
<br>


### 4.1. Scenario 1 - The user has a valid legacy SSO token in the browser, and accesses the authentication tree
- The user (not previously migrated) authenticates first in the legacy IAM.
- The user accesses the authentication tree.
- Upon accessing the tree, the user is automatically logged in, because the legacy SSO token is present in the browser and is valid. As a result, a user profile is created in ForgeRock IDM and AM, with no password set.
<br><br>
![Scenario1](images/Scenario1.png)
<br>

### 4.2. Scenario 2 - The user accesses the authentication tree, with no legacy SSO token in the browser, after previously accessing Scenario 1 - was created with no password
- The user accesses the authentication tree. The tree prompts the user for the username and password.
- After providing credentials, the user is successfully authenticated. This happens because the user was logged in successfully in the legacy IAM system. Since a Data Store Decision node returned false but the user was already migrated, and the legacy login is successful, the password is also updated in DS.
<br><br>
![Scenario2](images/Scenario2.png)
<br>

### 4.3. Scenario 3 - The user is not migrated, does not have a valid legacy SSO token, and accesses the authentication tree
- The user accesses the authentication tree. The tree prompts the user for the username and password.
- After providing credentials, the user is successfully authenticated. This happens because the user was logged in successfully in the legacy IAM, and the user's profile was successfully provisioned in ForgeRock DS, including the password.
<br><br>
![Scenario3](images/Scenario3.png)
<br>

### 4.4. Scenario 4 - The user is already migrated, and the Data Store Decision node authenticates the user successfully
- The user accesses the authentication tree. The tree prompts the user for the username and password.
- The user is authenticated automatically with both legacy IAM and a ForgeRock token at the end of tree processing.


## 5. Extending & Customizing
Any changes you need to make to adapt to a specific legacy system can be done in the provided sample nodes. To do so, you first need to import the project you downloaded - /forgerock-am-migration-sso-jit/openam-modernize-auth-nodes from GitHub. The node classes and additional files are described in <b>Chapter 3.2 - Tree Nodes</b>.
+ <b>Example</b>: To add or remove additional profile attributes, the node <b>CreateUser</b> can be updated. In the method <b>process</b>, you can retrieve additional attributes from the legacy profile details call. You can then map them to the request body in the <b>createProvisioningRequestEntity()</b> method.

## 6. Troubleshooting Common Problems
+ <b>Problem:</b> Changes in configuration don't appear in the AM console after deploying them.<br>
<b>Solution:</b> Make sure to imcrement the plugin version from the method getPluginVersion() associated with the modified node if any changes have been made to the configuration of a node in the Java class or the properties file.
<br><br>
+ <b>Problem:</b> No nodes, not even the out-of-the-box ones, are displayed in the tree creation screen.<br>
<b>Solution:</b> Check the nodes <b>@Attribute(order = number)</b> annotations. This can happen if two or more properties in the same node, have the same <b>order = number</b>.

## 7. Known issues
+ N/A

## 8. License

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
