# Disclaimer
The sample code described herein is provided on an "as is" basis, without warranty of any kind, to the fullest extent permitted by law. ForgeRock does not warrant or guarantee the individual success developers may have in implementing the sample code on their development platforms or in production configurations.
<br><br>
ForgeRock does not warrant, guarantee or make any representations regarding the use, results of use, accuracy, timeliness or completeness of any data or information relating to the sample code. ForgeRock disclaims all warranties, expressed or implied, and in particular, disclaims all warranties of merchantability, and warranties related to the code, or any service or software related thereto.
<br><br>
ForgeRock shall not be liable for any direct, indirect or consequential damages or costs of any type arising out of any action taken by you or others related to the sample code.

# Modernize Accelerators - SSO Toolkit (with IG)
With deployments of tens or hundreds of legacy applications, migration waves may be required to minimize the operational impact on production systems. With this type of use case, coexistence and SSO between legacy IAM and ForgeRock IAM is often needed.

## 1. Contents
The toolkit provides a collection of resources that can handle very complex migration scenarios, including bidirectional SSO between legacy IAM and ForgeRock AM.
The framework can be easily extended to support migrations from any legacy IAM platform that is capable of exposing client SDKs/APIs for operations such as:
    - Validating existing legacy IAM tokens
    - Using an authentication API (with a username and password input)

### 1.1. Assets Included
Bidirectional SSO capability between legacy IAM and ForgeRock IAM helps minimize risk and time to market in complex migration projects.
ForgeRock understands customers' needs to speed up migration design decisions and cut implementation time, and is thus delivering the following assets as part of the Migration Accelerators:

```
System         | Type                | Name                               | Description
---------------|---------------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------
Framework      | jar library         | openig-modernize-library-x.y.z.jar | A library containing the main interfaces provided for implementing the migration components
Client         | Java class          | LegacyOpenSSOProvider.java         | Example implementation of the openig-modernize-library-x.y.z.jar framework
IG             | jar library         | openig-modernize-filters           | The IG library holding the migration filters which use the implementation of the openig-modernize-library-x.y.z.jar framework
IG             | Route               | Legacy AM Authenticate Route       | A route that covers the authentication endpoint
IG             | Route               | Legacy AM Generic Route            | A route that covers all the resource endpoints from the legacy IAM
IG             | Route               | Agent Legacy Protected App         | 
IG             | Route               | Web Legacy Protected App           | A route that covers all the resource endpoints for the legacy protected application
```

## 2. Building The Source Code

+ <b>Important note:</b> The assets presented below are built based over OpenIG version 6.5.1.

In order to build the project from the command line, follow the steps presented below. Make sure that you have all the prerequisites installed correctly before starting.

### 2.1. Prerequisites - Prepare Your Environment

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

+ The source files use some Spring dependencies from the 5.2.1.RELEASE. The resources can be accessed in the folder [resources](todolink).

+ [spring-beans-5.2.1.RELEASE](https://mvnrepository.com/artifact/org.springframework/spring-beans)
+ [spring-core-5.2.1.RELEASE](https://mvnrepository.com/artifact/org.springframework/spring-core)
+ [spring-jcl-5.2.1.RELEASE](https://mvnrepository.com/artifact/org.springframework/spring-jcl)
+ [spring-web-5.2.1.RELEASE](https://mvnrepository.com/artifact/org.springframework/spring-web)

### 2.2. Getting the repository

If you want to get the assets contained in this package, you must start by cloning the ForgeRock repository:

```
mkdir demo
git clone https://github.com/ForgeRock/modernize-accelerators.git
cd forgerock-ig-migration-sso-jit
```

### 2.3. Resources explained

#### 2.3.1. The framework

In the [resources](todolink) folder you will find the pre-built jar file named openig-modernize-library.x.y.x.jar. This jar file is not editable and contains the base interface with the pre-defined mandatory methods that need to be implemented for any migration project.
You should use this jar file as a dependency, and implement the class <b>LegacyIAMProvider</b>.

+ LegacyIAMProvider contains the 3 base methods that must be implemented:
    + getUserCredentials - Implementation must read the user credentials from the Forgerock HTTP Request. The HTTP request gives flexibility to capture the user's credentials from the request headers or from the request body. Should output a User object with the intercepted username and password.
    + getExtendedUserAttributes - Get user profile attributes from the legacy IAM, with userName as input.
    + validateLegacyAuthResponse - Validate if the authentication response from the legacy system is successfull.

To use the library as a dependency, you can add it to your lib directory for a simple java project, or import it to your maven or gradle project as an artifact.

Example that shows how to install the jar as a maven artifact on a local system:

```
mvn install:install-file \
   -Dfile='/path/to/openig-modernize-library.x.y.x.jar' \
   -DgroupId=forgerock.modernize \
   -DartifactId=modernize \
   -Dversion=0.0.1 \
   -Dpackaging=jar \
   -DgeneratePom=true
```

Example that shows how the installed jar file can be used within a maven's project pom.xml:

```
<dependency>
	<groupId>forgerock.modernize</groupId>
	<artifactId>modernize</artifactId>
	<version>0.0.1</version>
</dependency>
```

#### 2.3.2. The framework implementation project

This [project](todolink) holds an example of code that implements the framework described in section 2.3.1. 
We can see that the 3 main methods of the interface from the framework are implemented here, along with other methods that do specific actions for the client's IAM platform.

This project uses a config file for any configurable properties you might need. The <b>config.properties</b> gile is located under /src/maine/resources.

#### 2.3.3. The IG filters



### 2.3. Building the Code

The build process and dependencies are managed by Maven. The first time you build the project, Maven will pull down all the dependencies and Maven plugins required by the build, which can take a longer time. 
Subsequent builds will be much faster!

```
cd modernize-accelerators/forgerock-ig-migration-sso-jit/openig-modernize-auth-nodes
mvn package
```

Maven builds the binary in `openig-modernize-filters/target/`. The file name format is `openam-modernize-auth-nodes-<nextversion>-SNAPSHOT.jar` . 
For example, "openam-modernize-auth-nodes-1.0.0-SNAPSHOT.jar".


### 2.4. Building the OpenIG war file

+ Download and unzip the IG.war file from ForgeRock BackStage:

```
https://backstage.forgerock.com/downloads/browse/ig/latest
mkdir ROOT && cd ROOT
jar -xf ~/Downloads/IG-6.5.2.war
```

+ Copy the following files in the /ROOT/WEB-INF/lib folder:
    + the newly generated JAR file `cp ~/openig-modernize-filters-<nextversion>-SNAPSHOT.jar WEB-INF/lib`

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

- <b>Check Legacy Token</b> - Custom node provided in this SSO toolkit. Detects if an existing legacy token exists in the browser in a specific cookie, and validates this as an active token against the legacy IAM system via an SDK/API call. The default node uses a GET API call with the cookie fetched from the incoming http request. The name of the cookie and the target URL is configurable. The node is vendor-specific and is flexible enough to be tailored for each vendor. The Oracle plugin provides a custom implementation for this node using the Oracle Access Client SDK.

```
Node class: /src/main/java/org.forgerock.openam.auth.node.CheckLegacyToken.java
Plugin class: /src/main/java/org.forgerock.openam.auth.node.plugin.CheckLegacyTokenPlugin.java
Configuration file: /src/main/resources/org/forgerock/openam/auth/node/CheckLegacyToken.properties

Configuration          | Example                                                            | Description
---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------
Legacy Token Endpoint  | <<proto>>://<<host>>/openam/json/sessions?tokenId=                 | End point used by the legacy IAM to verify if an SSO token is valid
Legacy Cookie Name     | iPlanetDirectoryPro                                                | Name of the SSO token expected by the legacy token verification end point
```

<br>

- <b>Is User Migrated</b> - Custom node provided in the SSO toolkit. Calls the default ForgeRock IDM managed object API to determine whether the user is already migrated. This node is generic, and does not need to be customized for specific legacy IAM vendor implementations.

```
Node class: /src/main/java/org.forgerock.openam.auth.node.CheckUserMigrationStatus.java
Plugin class: /src/main/java/org.forgerock.openam.auth.node.plugin.CheckUserMigrationStatusPlugin.java
Configuration file: /src/main/resources/org/forgerock/openam/auth/node/CheckUserMigrationStatus.properties

Configuration          | Example                                                            | Description
---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------
IDM User Endpoint      | <<proto>://<<host>>/openidm/managed/user?_queryFilter=userName+eq+ | End point used to verify in ForgeRock IDM if the user is migrated
IDM Admin User         | idmAdmin                                                           | IDM admin user used to query the IDM user endpoint
IDM Admin Password     | idmPassword                                                        | IDM admin password used to query the IDM user endpoint
```

<br>

- <b>Page Node</b> - The default page node in ForgeRock IAM used to capture user credentials. This node is generic, and does not need to be customized for specific legacy IAM vendor implementations.

<br>

- <b>Create User in DS</b> - Custom node provided in the SSO toolkit. Calls the default ForgeRock IDM managed object API to provision the user in ForgeRock. This node is generic, and does not need to be customized for specific legacy IAM vendor implementations. The node uses the following shared state attributes: 

```
Node class: /src/main/java/org.forgerock.openam.auth.node.CreateUser.java
Plugin class: /src/main/java/org.forgerock.openam.auth.node.plugin.CreateUserPlugin.java
Configuration file: /src/main/resources/org/forgerock/openam/auth/node/CreateUser.properties

Configuration          | Example                                                            |Description
---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
Legacy URL             | <<proto>>://<<host>>/openam/json/realms/root/realms/legacy/users/  | End point used to get profile attributes from the legacy IAM
IDM User Endpoint      | <<proto>>://<<host>>/openidm/managed/user?_action=create           | End point used to create a user in ForgeRock IDM
IDM Admin User         | idmAdmin                                                           | IDM admin user used to query the IDM user endpoint
IDM Admin Password     | idmPassword                                                        | IDM admin password used to query the IDM user endpoint
Set Password Reset     | true/false - on/off                                                | Switch used to determine if the node is used on a scenario that cannot migrate the user password. Set to true if the password can't be migrated.
```

<br>

- <b>Data Store Decision</b> - This is the default node for credential validation in ForgeRock IAM. This node is generic, and does not need to be customized for specific legacy IAM vendor implementations.

<br>

- <b>Legacy Log In</b> - Custom node provided in the SSO toolkit. Validates credentials (username and password) entered by the user against the legacy IAM system via an SDK/API call. The default node uses a POST API call with the username and password fetched from the shared state. The URL is configurable, the node expects a successful response of 200 OK and a specific cookie to be present in the response. The cookie name is configurable. The node is vendor-specific and is flexible enough to be tailored for each vendor. The Oracle plugin provides a custom implementation for this node using the Oracle Access Client SDK.

```
Node class: /src/main/java/org.forgerock.openam.auth.node.LegacyLogin.java
Plugin class: /src/main/java/org.forgerock.openam.auth.node.plugin.LegacyLoginPlugin.java
Configuration file: /src/main/resources/org/forgerock/openam/auth/node/LegacyLogin.properties

Configuration          | Example                                                            | Description
---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------
Legacy Token Endpoint  | <<proto>>://<<host>>/json/realms/root/realms/legacy/authenticate   | End point used by the Legacy iAM to authenticate an user
Legacy Cookie Name     | iPlanetDirectoryPro                                                | Name of the SSO token returned by the legacy IAM for a successful authentication.
```

<br>

- <b>Set Password in DS</b> - Custom node provided in the SSO toolkit. Calls the default ForgeRock IDM managed object API to provision the user password in ForgeRock IAM. This node is generic one, and does not need to be customized for specific legacy IAM vendor implementations.

```
Node class: /src/main/java/org.forgerock.openam.auth.node.SetUserPassword.java
Plugin class: /src/main/java/org.forgerock.openam.auth.node.plugin.SetUserPasswordPlugin.java
Configuration file: /src/main/resources/org/forgerock/openam/auth/node/SetUserPassword.properties

Configuration          | Example                                                                           | Description
---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------
IDM User Endpoint      | <<proto>>://<<host>>/openidm/managed/user?_action=patch&_queryFilter=userName+eq+ | End point used to create a user in ForgeRock IDM
IDM Admin User         | idmAdmin                                                                          | IDM admin user used to query the IDM user endpoint
IDM Admin Password     | idmPassword                                                                       | IDM admin password used to query the IDM user endpoint
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
