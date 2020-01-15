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

+ The source files use some Spring dependencies from the 5.2.1.RELEASE. The resources can be accessed in the folder [resources](https://github.com/ForgeRock/modernize-accelerators/tree/develop/forgerock-ig-migration-sso-jit/resources).

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

In the [resources](https://github.com/ForgeRock/modernize-accelerators/tree/develop/forgerock-ig-migration-sso-jit/resources) folder you will find the pre-built jar file named openig-modernize-library.x.y.x.jar. This jar file is not editable and contains the base interface with the pre-defined mandatory methods that need to be implemented for any migration project.
You should use this jar file as a dependency, and implement the class <b>LegacyIAMProvider</b>.

If you need to update this framework yourself, you can do so by importing the [openig-modernize-library](https://github.com/ForgeRock/modernize-accelerators/tree/develop/forgerock-ig-migration-sso-jit/openig-modernize-library) project into your preferred IDE.

+ LegacyIAMProvider contains the 3 base methods that must be implemented:
    + getUserCredentials - Implementation must read the user credentials from the Forgerock HTTP Request. The HTTP request gives flexibility to capture the user's credentials from the request headers or from the request body. Should output a User object with the intercepted username and password.
    + getExtendedUserAttributes - Get user profile attributes from the legacy IAM, with userName as input.
    + validateLegacyAuthResponse - Validate if the authentication response from the legacy system is successfull.

To use the library as a dependency, you can add it to your lib directory for a simple java project, or import it to your maven or gradle project as an artifact.

Example for installing the jar as a maven artifact on a local maven repository:

```
mvn install:install-file \
   -Dfile='/path/to/openig-modernize-library.x.y.x.jar' \
   -DgroupId=forgerock.modernize \
   -DartifactId=modernize \
   -Dversion=0.0.1 \
   -Dpackaging=jar \
   -DgeneratePom=true
```

Example usage of the jar file in a maven's project pom.xml:

```
<dependency>
	<groupId>forgerock.modernize</groupId>
	<artifactId>modernize</artifactId>
	<version>0.0.1</version>
</dependency>
```

#### 2.3.2. The framework implementation

The [openig-modernize-client-implementation](https://github.com/ForgeRock/modernize-accelerators/tree/develop/forgerock-ig-migration-sso-jit/openig-modernize-client-implementation) project holds an example of code that demonstrated the usage and implementation of the framework described in section 2.3.1. 
We can see that the 3 main methods of the interface from the framework are implemented here, along with other methods that do specific actions for the user's IAM platform.

This project uses a config file for any configurable properties you might need. The <b>config.properties</b> file is located under /src/main/resources.

#### 2.3.3. The IG filters

The [openig-modernize-filters](https://github.com/ForgeRock/modernize-accelerators/tree/develop/forgerock-ig-migration-sso-jit/openig-modernize-filters) project brings together all the resources presented so far, and leads the sequence of actions required for SSO and JIT. This is orchestrated by the filter <b>MigrationSsoFilter</b>.

#### 2.3.4. The IG routes

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
jar -xf ~/Downloads/IG-6.5.1.war
```

+ Copy the following files in the /ROOT/WEB-INF/lib folder:
    + the jar files from [resources](https://github.com/ForgeRock/modernize-accelerators/tree/develop/forgerock-ig-migration-sso-jit/resources) folder: `cp ~/resources/* WEB-INF/lib`
	Note: If you modified the framework project and generated a new jar, you must copy your own version of the jar instead of the default one found in the folder.
    + the jar file containing the IG filters: `cp ~/openig-modernize-filters-<nextversion>-SNAPSHOT.jar WEB-INF/lib`
	+ the jar file containing the framework implementation: `cp ~/openig-modernize-filters-<nextversion>-SNAPSHOT.jar WEB-INF/lib`

+ Rebuild the WAR file: 

```
jar -cf ../ROOT.war *
```

+ Copy and deploy the ROOT.war file in the container in which IG is deployed.

## 3. Configuration

### 3.1. Routes


### 3.2. Others


## 4. Scenarios


### 4.1. Scenario 1 - Migrated user accesses the authentication route that protects legacy IAM without


### 4.2. Scenario 2 - Non-migrated user accesses the authentication route that protects legacy IAM without


## 5. Extending & Customizing
Any changes you need to make to adapt to a specific legacy system can be done in the provided sample projects. To do so, you first need to import the projects you downloaded - https://github.com/ForgeRock/modernize-accelerators/tree/develop/forgerock-ig-migration-sso-jit from GitHub.

## 6. Troubleshooting Common Problems

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
