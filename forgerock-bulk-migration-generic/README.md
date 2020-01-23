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
IDM	       | Managed Object      | managed.json			  | Enhanced user object definition that brings several other typical attributes in the IDM definition.
IDM	       | Mapping             | sync.json			  | Source mapping set for Legacy IAM to IDM managed object.
IDM            | Mapping             | sync.json			  | Source mapping set for IDM managed object to Forgerock DS.
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
TBD 
The mappings and configuration provided with this toolkit serves as an example of implementation but it can be adapted to any source repository and also to any attributes that are needed in the functional usecases.

+ [managed.json](https://github.com/ForgeRock/modernize-accelerators/blob/develop/forgerock-bulk-migration-generic/openidm-modernize-config/conf/managed.json)

```
Configuration                 | Example                                                            | Description
----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------
User Managed ObjectAttributes | TBD                                                                | TBD
```

+ [sync.json](https://github.com/ForgeRock/modernize-accelerators/blob/develop/forgerock-bulk-migration-generic/openidm-modernize-config/conf/sync.json)

```
Configuration               | Example                                                            | Description
--------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------
Mapping Legacy -> IDM       | TBD                                                                | TBD
Mapping IDM -> Forgerock DS | TBD                                                                | TBD
```

+ [provisioner.openicf-FRDS.json](https://github.com/ForgeRock/modernize-accelerators/blob/develop/forgerock-bulk-migration-generic/openidm-modernize-config/conf/provisioner.openicf-FRDS.json)

```
Configuration               | Example                                                            | Description
--------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------
LDAP Server Hostname        | TBD                                                                | TBD
LDAP Server Port            | TBD                                                                | TBD
LDAP Server Bind Username   | TBD                                                                | TBD
LDAP Server Bind Password   | TBD                                                                | TBD
LDAP Server ObjectClass     | TBD                                                                | TBD
```

+ [provisioner.openicf-FRDS.json](https://github.com/ForgeRock/modernize-accelerators/blob/develop/forgerock-bulk-migration-generic/openidm-modernize-config/conf/provisioner.openicf-FRDS.json)

```
Configuration               | Example                                                            | Description
--------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------
LDAP Server Hostname        | TBD                                                                | TBD
LDAP Server Port            | TBD                                                                | TBD
LDAP Server Bind Username   | TBD                                                                | TBD
LDAP Server Bind Password   | TBD                                                                | TBD
LDAP Server ObjectClass     | TBD                                                                | TBD
```

### 3.2. Install config files
Before copying the config files (under /conf location on the github repository, you should change accordingly all the properties inside them).

Copy the content of the /conf folder to your IDM /conf location and restart the server. To check if the changes were properly applied you can login to the IDM Administration Console (using the openidm_admin or another admin account if it was created before) and access from the menu: Configure -> Connectors and Configure -> Mappings to see the new assets.


## 4. Extending & Customizing
TBD

## 5. Troubleshooting Common Problems
+ N/A

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