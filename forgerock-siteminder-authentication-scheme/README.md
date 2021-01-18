# Disclaimer
The sample code described herein is provided on an "as is" basis, without warranty of any kind, to the fullest extent permitted by law. ForgeRock does not warrant or guarantee the individual success developers may have in implementing the sample code on their development platforms or in production configurations.
<br><br>
ForgeRock does not warrant, guarantee or make any representations regarding the use, results of use, accuracy, timeliness or completeness of any data or information relating to the sample code. ForgeRock disclaims all warranties, expressed or implied, and in particular, disclaims all warranties of merchantability, and warranties related to the code, or any service or software related thereto.
<br><br>
ForgeRock shall not be liable for any direct, indirect or consequential damages or costs of any type arising out of any action taken by you or others related to the sample code.

# Modernize IAM Accelerators - Siteminder Authentication Scheme
With deployments of tens or hundreds of legacy applications, migration waves may be required to minimize the operational impact on production systems. With this type of use case, coexistence and SSO between Siteminder and ForgeRock IAM is often needed.
Sometimes putting IG in front of a legacy system is not an option for commercial reasons.

## 1. Contents
This toolkit contains an authentication scheme that enables Siteminder to recognize ForgeRock AM session cookies.

### 1.1. Assets Included
This package contains the Siteminder authentication scheme org.forgerock.openam.authentication.modules.siteminder.OpenAMAuthScheme. This authentication scheme must be deployed on the Siteminder platform.

## 2. Building The Source Code

+ <b>Important notes:</b> 
    + The assets presented in this package are built based on AM version 6.5.

Make sure that you have all the prerequisites installed correctly before starting.

### 2.1. Prerequisites - Prepare Your Environment

#### 2.1.1. Software and Environment

You will need the following software to build the code:

| Software               | Required Version |
| ---------------------- | ---------------- |
| Java Development Kit   | 1.8              |
| Maven                  | 3.1.0 and above  |
| Git                    | 1.7.6 and above  |

Set the following environment variables:

- `JAVA_HOME` - points to the location of the version of Java that Maven will use.
- `MAVEN_HOME` - points to the location of the Maven installation and settings.
- `MAVEN_OPTS` - sets some options for the jvm when running Maven.

For example your environment variables should look like this:

```
JAVA_HOME=/usr/jdk/jdk-1.8
MAVEN_HOME=/opt/apache-maven-3.6.3
MAVEN_OPTS='-Xmx2g -Xms2g -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=512m'
```

Note: You need access to the ForgeRock private-releases maven repository, and your maven build should point to the settings.xml file downloaded with your backstage account. For more information regarding getting access to the ForgeRock protected repositories, see this [knowledge article](https://backstage.forgerock.com/knowledge/kb/article/a74096897)

#### 2.1.2. External libraries

+ The migration toolkit uses the Siteminder Java AgentAPI. Download the SDK from your Siteminder support page, or get the required .jar files from your existing Siteminder Web Agent installation. The migration toolkit requires the following jar files:
    + smjavaagentapi.jar
    + SmJavaApi.jar
	
+ Copy the SDK jars inside the project directory /libSM

### 2.2. Getting the Code

If you want to run the code unmodified, clone the ForgeRock repository:

```
mkdir demo
cd demo
git clone https://github.com/ForgeRock/modernize-accelerators.git
```

### 2.3. Building the Code

The build process and dependencies are managed by Maven. The first time you build the project, Maven pulls 
down all the dependencies and Maven plugins required by the build, which can take a while. Subsequent builds are much faster!


```
cd modernize-accelerators/forgerock-siteminder-authentication-scheme
mvn package
```

## 3. Installing the authentication scheme on the Siteminder policy server

+ Log into the CA Policy Server Admin UI and perform the following tasks:
    + Create a custom authentication scheme as shown on the next picture. Note the “debug” parameter should be removed in production.
	![Step1](images/Step1.png)