# Disclaimer
The sample code described herein is provided on an "as is" basis, without warranty of any kind, to the fullest extent permitted by law. ForgeRock does not warrant or guarantee the individual success developers may have in implementing the sample code on their development platforms or in production configurations.
<br><br>
ForgeRock does not warrant, guarantee or make any representations regarding the use, results of use, accuracy, timeliness or completeness of any data or information relating to the sample code. ForgeRock disclaims all warranties, expressed or implied, and in particular, disclaims all warranties of merchantability, and warranties related to the code, or any service or software related thereto.
<br><br>
ForgeRock shall not be liable for any direct, indirect or consequential damages or costs of any type arising out of any action taken by you or others related to the sample code.

# Modernize IAM Accelerators - Bulk User Migration Toolkit - Oracle Unified Directory (OUD) 
One-time and incremental import of user profiles and groups from Legacy OUD LDAP store to Forgerock DS is often a requirement in complex migration process.
With custom schema being used by Legacy IAM systems (custom object classes, custom attributes, custom naming and organization units/suffix) the process of synchronizing information can be complex to design and implement. In addition, the particular mapping of the extended schema (attributes, object classes and group-membership used for core IAM transactions) can also be cumbersome and lengthy.

# What's New
Please find below the new features and improvements for the 7.0 Miami Accelerators

### <h2>ForgeRock Modernize IAM Accelerators Oracle Plug In Updates</h2>

### <i>Updated - Modernize IAM Accelerators - Bulk User Migration Toolkit - Oracle Unified Directory (OUD)</i>

+ Updated bundle versions and configurations for:
	+ provisioner.openicf-ldap.json
	+ provisioner.openicf-legacyIAM.json
	+ sync.json

## 1. Contents
The following assets have been included in the Migration Accelerators for this purpose:
	- Template for OUD LDAPv3 to LDAPv3 user reconciliation from Legacy IAM to Forgerock DS;
	- Mapping for common group information: common name, description, displayName, uniqueMember;
	- Mapping for common identity information: UID, password, common name, group membership, status, mail, telephone number, given name, last name, department details, description, employee details, last login, account locked features, number of wrong attempts.

### 1.1. Assets Included
This toolkit implements one-way synchronization from an external Legacy OUD userstore to the Forgerock IDM repository and then synchronization to Forgerock Directory Server as the next generation userstore.
	- User and group objects in the source system file are synchronized with the managed users and groups in the Forgerock IDM repository and then are pushed in Forgerock DS based on the provided mappings;
	- Both inbound mappings and outbound mappings can be extended for the specific customer scenarios;
	- The sample source connector is LDAPv3 but may be adapted in the customer context.


System	| Type                | Name                 	          	| Description
--------|---------------------|---------------------------------------- | --------------------------------------------------------------------------------------------------------
IDM	| Managed Object      | managed.json			  	| Enhanced user object definition that brings several other typical attributes in the IDM definition
IDM	| Managed Object      | managed.json			  	| New group managed object definition
IDM	| Mapping             | sync.json			  	| Source mapping set for Legacy OUD to IDM User managed object
IDM	| Mapping             | sync.json			  	| Source mapping set for Legacy OUD to IDM Group managed object
IDM	| Mapping             | sync.json			  	| Source mapping set for IDM User managed object to Forgerock Directory Server
IDM	| Mapping             | sync.json			  	| Source mapping set for IDM Group managed object to Forgerock Directory Server
IDM	| Connector           | provisioner.openicf-legacyOUD.json	| Source connector that pulls user identities from Legacy OUD (LDAPv3 connector)
IDM	| Connector           | provisioner.openicf-ldap.json      	| Target connector that pushes identity information inside Forgerock Directory Server (LDAPv3 connector)


## 2. Getting the repository

If you want to get the assets contained in this package, you must start by cloning the ForgeRock repository:

```
mkdir demo
git clone https://github.com/ForgeRock/modernize-accelerators.git
cd forgerock-bulk-migration-oud
```

## 3. Configuration

### 3.1. JSON config files
 
The mappings and configuration provided with this toolkit serves as an example of implementation but it can be adapted to any source repository and also to any attributes that are needed in the functional usecases.

+ [managed.json](openidm-modernize-config/conf/managed.json)


Configuration               	| Change type          		| Description
------------------------------- | ----------------------------- | ---------------------------------------------------------------------------------------------------
User Managed Object		| Update                   	| The existing IDM User managed object was extended with some attributes OUD specific
Group Managed Object		| New				| A new IDM managed object was created in order to store the OUD groups	


+ [provisioner.openicf-legacyOUD.json](openidm-modernize-config/conf/provisioner.openicf-legacyOUD.json)


Configuration               	| Change type          		| Description
------------------------------- | ----------------------------- | --------------------------------------------------------------------------------------------------------------
LDAPv3 OUD Connector		| New                   	| A new LDAPv3 Connector that connects to the OUD repository for retrieving user and group information


+ [provisioner.openicf-ldap.json](openidm-modernize-config/conf/provisioner.openicf-ldap.json)


Configuration               	| Change type          		| Description
------------------------------- | ----------------------------- | --------------------------------------------------------------------------------------------------------------
LDAPv3 Forgerock DS Connector	| Update               		| A new LDAPv3 Connector that connects to the Forgerock DS repository for pushing the user and group information


+ [sync.json](openidm-modernize-config/conf/sync.json)


Configuration               	| Change type			| Description
------------------------------- | ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------
Mapping OUD Ldap -> IDM User   	| New				| A new mapping definition was created in order to map the target OUD user attributes to the IDM User managed object ones
Mapping OUD Ldap -> IDM Group  	| New				| A new mapping definition was created in order to map the target OUD group attributes to the IDM Group managed object ones
Mapping IDM User -> Forgerock DS| Update			| The existing mapping was updated in order to pass to the Forgerock DS the additional user attributes OUD specific
Mapping IDM Group -> Forgerock 	| New				| A new mapping was created in order to pass to the Forgerock DS the group atributes that were previously reconciled from OUD



### 3.2. Install config files
+ <b>Important note:</b> The assets presented below are built based on OpenIDM version 7.0.1.

Before copying the config files (under /conf location on the github repository, you should change accordingly all the properties inside them):
+ connection details to the <b>OUD repository</b>
+ connection details to the <b>Forgerock Directory Server</b>

Copy the content of the /conf folder to your IDM /conf location and restart the server. To check if the changes were properly applied you can login to the IDM Administration Console (using the openidm-admin or another admin account if it was created before) and access from the menu: 
+ <b>Configure</b> -> <b>Managed Objects</b> (check if the User managed object has the new attributes and if the new Group managed object exists);
+ <b>Configure</b> -> <b>Connectors</b> (validate that the two LDAPv3 connectors are in Active state);
+ <b>Configure</b> -> <b>Mappings</b> (check if the two mappings are presents).


### 3.3. Prerequisite in order to synchronize hashed passwords

You can use passwords in DS/OpenDJ that are taken from Legacy OUD (SSHA512 algorithm) as follows:
+ Configure DS/OpenDJ to allow <b>pre-encoded passwords</b> for the relevant password policy. You can set this using dsconfig, for example:

    $ ./dsconfig set-password-policy-prop --policy-name "Default Password Policy" --port 4444 --bindDN "cn=Directory Manager" --bindPassword password --advanced --set allow-pre-encoded-passwords:true --trustAll --no-prompt


## 4. Extending & Customizing

### 4.1. Extending the Legacy IAM connector with other attributes and configuration
Please see the ForgeRock [documentation](https://backstage.forgerock.com/docs/idm/7/connector-reference/chap-ldap.html#ldap-connector-config) for information about how to create and update a generic LDAPv3 connector.

### 4.2. Extending the mapping
Please see the ForgeRock [documentation](https://backstage.forgerock.com/docs/idm/7/synchronization-guide/mapping-resources.html) for information about how to create and update a mapping.

### 4.3. Schedule reconciliation
Please see the ForgeRock [documentation](https://backstage.forgerock.com/docs/idm/7/synchronization-guide/configuring-sync-schedule.html#configuring-sync-schedule) for information about how to create and update a mapping.


## 5. Troubleshooting Common Problems
+ N/A

## 6. Known issues
+ N/A

## 7. License

This project is licensed under the Apache License, Version 2.0. The following text applies to both this file, and should also be included in all files in the project:

```
/***************************************************************************
 *  Copyright 2019-2021 ForgeRock AS
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
