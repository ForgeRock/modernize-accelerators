# MiAMI Accelerators

# IG Setup
This project contains a number of custom java filters and routes for ForgeRock IG configurations.
	
The IG routes included in this project are:
* agent-legacy-protected-app-route.json
* j2ee-legacy-protected-app.json
* legacy-am-authenticate-route.json
* legacy-am-generic-route.json
* web-legacy-protected-app-route.json

The IG custom filters included in this project are:
* HeaderAuthenticationProvisioningFilter

## Build and deploy custom java filters
> Please see the dedicated README for details on how to build and deploy these java assets: [OpenIG/openig-miami-filters/README.md](OpenIG/openig-miami-filters/README.md)

## IG Configuration 

### Routes

Copy the content of the /config folder to your IG /config location. By default, the IG configuration files are located in the directory `$HOME/.openig` (on Windows, `%appdata%\OpenIG`). 


# Troubleshooting

## TBD

# BUGS

## TBD

## License

>  Copyright 2019 ForgeRock AS
>
> Licensed under the Apache License, Version 2.0 (the "License");
> you may not use this file except in compliance with the License.
> You may obtain a copy of the License at
>
>    http://www.apache.org/licenses/LICENSE-2.0
>
>  Unless required by applicable law or agreed to in writing, software
>  distributed under the License is distributed on an "AS IS" BASIS,
>  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
>  See the License for the specific language governing permissions and
>  limitations under the License.
