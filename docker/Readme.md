# Table of contents

 - [Running the code](#run1)
   - [Running everything in docker](#run2)
     - [Clone git repo](#clone)
     - [User specific configuration](#config)
     - [Run Docker](#docker)
     - [Examples](#examples)
 
# <a name="run1"></a>Running the code
This repository is designed to be run in Docker out of the box using docker-compose. Optionally the user can make minor configuration changes to run portions of the project on their local machine for easier programmatic interaction with Mobius directly.

## <a name="run2"></a>Running everything in docker
### <a name="clone"></a>Clone git repo
```
git clone https://github.com/RENCI-NRIG/CICI-SAFE.git
cd ./CICI-SAFE/docker
```
### <a name="config"></a>User specific configuration
Once images are ready, update configuration in docker as indicated below:
1. Update docker/config/sdx.conf to specify user specific values for following properties
```
 slicename="sdx-kthare10"
 sshkey="./ssh/id_rsa"
 exogenipem="./ssh/geni-kthare10.pem"
```
2. Update docker-compose.yml for sdxserver to point the below parameters to user specific locations. User needs to modify the values before the colon to map to location on host machine.
```
        # point to user specific keys below
         volumes:
         - "~/.ssh/geni-kthare10.pem:/code/ssh/geni-kthare10.pem"
         - "~/.ssh/id_rsa.pub:/code/ssh/id_rsa.pub"
         - "~/.ssh/id_rsa:/code/ssh/id_rsa"
```
### <a name="run3"></a>Run Docker
Run docker-compose up -d from CICI-SAFE/docker directory

```
$ docker-compose up -d
Creating database ... done
Creating rabbitmq ... done
Creating mobius   ... done
Creating notification ... done
```
After a few moments the docker containers will have stood up and configured themselves. User can now trigger requests to Sdxserver. 
