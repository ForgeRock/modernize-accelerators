# What's New
This chapter covers the new features and improvements for the 7.0 Miami Accelerators

## Updated - Modernize IAM Accelerators - AM Based Bi-Directional SSO and JIT Toolkit

+ Switched to using the AM 7 identity management nodes
    + Decomissioned Legacy-FR-Set Password node and replaced it with Patch Object node
    + Decomissioned Legacy-FR-Migration Status node and replaced it with Identify Existing User node
	+ Removed the IDM managed user API call from the Legacy-FR-Create FR User node. This node has been updated to fetch the user information and add it to the shared state on the objectAttributes object.
	+ Create Object node is used to create the migrated user.
+ New node: Add Attributes To Object Attributes - this node reads the attributes specified as keys from shared state, and adds them to the fields specified as values on the objectAttributes object of the user that needs to be migrated.
+ New service: LegacyFRService - this service holds all the configurations related to the legacy IAM systems.


## Updated - Modernize IAM Accelerators - Bulk User Migration Toolkit

+ Updated bundle versions and configurations for:
	+ provisioner.openicf-ldap.json
	+ provisioner.openicf-legacyIAM.json
	+ sync.json


## Updated - Modernize IAM Accelerators - IG Based Bi-Directional SSO and JIT Toolkit

+ Added OAuth 2.0 integration between IG-AM-IDM 7.0.1 using the ClientCredentialsOAuth2ClientFilter
+ Removed configurations and API calls related to IDM user creation API calls
+ Updated code base to support Java 11 dependencies.


## Updated - Modernize IAM Accelerators - AM Based Bi-Directional SSO and JIT Toolkit - Migration from Siteminder 12.8 to ForgeRock

+ Switched to using the AM 7 identity management nodes
    + Decomissioned Legacy-SM-Set Password node and replaced it with Patch Object node
    + Decomissioned Legacy-SM-Migration Status node and replaced it with Identify Existing User node
	+ Removed the IDM managed user API call from the Legacy-SM-Create FR User node. This node has been updated to fetch the user information and add it to the shared state on the objectAttributes object.
	+ Create Object node is used to create the migrated user.
+ New node: Add Attributes To Object Attributes - this node reads the attributes specified as keys from shared state, and adds them to the fields specified as values on the objectAttributes object of the user that needs to be migrated.
+ New service: SiteminderService - this service holds all the configurations related to the Siteminder legacy systems.
+ Updated code base to support Java 11 dependencies.

## New - Modernize IAM Accelerators - Siteminder Authentication Scheme

+ Added the toolkit that contains an authentication scheme which enables Siteminder to recognize ForgeRock AM session cookies.



## Updated - Modernize IAM Accelerators - AM Based Bi-Directional SSO and JIT Toolkit - Migration from Oracle 11G OAM to ForgerRock

+ Switched to using the AM 7 identity management nodes
    + Decomissioned Legacy-ORA-Set Password node and replaced it with Patch Object node
    + Decomissioned Legacy-ORA-Migration Status node and replaced it with Identify Existing User node
	+ Removed the IDM managed user API call from the Legacy-ORA-Create FR User node. This node has been updated to fetch the user information and add it to the shared state on the objectAttributes object.
	+ Create Object node is used to create the migrated user.
+ New node: Add Attributes To Object Attributes - this node reads the attributes specified as keys from shared state, and adds them to the fields specified as values on the objectAttributes object of the user that needs to be migrated.
+ New service: OracleService - this service holds all the configurations related to the OAM legacy systems.
+ Updated code base to support Java 11 dependencies.

## Updated - Modernize IAM Accelerators - Bulk User Migration Toolkit - Oracle Unified Directory (OUD)

+ Updated bundle versions and configurations for:
	+ provisioner.openicf-ldap.json
	+ provisioner.openicf-legacyOUD.json
	+ sync.json