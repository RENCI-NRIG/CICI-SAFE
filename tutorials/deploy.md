# Deploy SDX

Install prerequisites

        sudo apt install -y maven openjdk-8-jdk docker.io

Generate ssh key (the default key pair created by ssh-keygen command  without options doesn't work on latest Ubuntu)

        ssh-keygen -t rsa -m PEM

Set the version and safe docker image, plexus sdn controller docker image and safe server script as environment variable. Set the public ip addresses of riak server and SDX controller. Run this in all nodes.

        SAFEIMG="yaoyj11/safeserver-v8"
        PLEXUSIMG="yaoyj11/plexus-v4"
        SAFE_SCRIPT="sdx-routing.sh"
        riak_ip="IP address of VM1"
        sdx_ip="IP address of sdx"
        riak_ip="128.194.6.194"
        sdx_ip="128.194.6.144"

## 1. deploy riak server on VM1
### a) 

        sudo apt install -y docker.io

### b) 

        sudo docker pull yaoyj11/riakimg
        sudo docker run -i -t  -d -p 2122:2122 -p 8098:8098 -p 8087:8087 -h riakserver --name riakserver yaoyj11/riakimg
        sudo docker exec -itd riakserver sudo riak start
        sudo docker exec -itd riakserver sudo riak-admin bucket-type activate  safesets
        sudo docker exec -itd riakserver sudo riak-admin bucket-type update safesets '{"props":{"allow_mult":false}}'

## 2. deploy safe server and plexus server on VM2
For security, plexus controller and safe server should only listen on localhost so that only the SDX controller can calls them.
###  a) deploy safe server

        sudo docker pull ${SAFEIMG}
        sudo docker run -i -t -d -p 7777:7777 -h safe --name safe ${SAFEIMG}
        sudo docker exec -itd safe /bin/bash -c  "cd /root/safe;sed -i 's/RIAKSERVER/$riak_ip/g' safe-server/src/main/resources/application.conf;./${SAFE_SCRIPT}"

###  b) deploy SDN controller

        publicurl="http://${sdx_ip}:8888"
        sudo docker pull ${PLEXUSIMG}
        sudo docker run -i -t -d -p 8080:8080 -p 6633:6633 -p 3000:3000 -h plexus --name plexus ${PLEXUSIMG}
        sudo docker exec -itd plexus /bin/bash -c  "cd /root;pkill ryu-manager; ryu-manager --log-file ~/ryu.log --default-log-level 1 ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/rest_router.py ryu/ryu/app/ofctl_rest.py --zapi-db-url ${publicurl}/sdx/flow/packetin"

## 3. deploy SDX controller on VM2

        export WORKING_DIR=~
        cd $WORKING_DIR
        git clone https://github.com/RENCI-NRIG/orca5.git
        cd orca5
        mvn install

        echo Git Ahab
        cd $WORKING_DIR
        cat .ssh/id_rsa.pub >>.ssh/authorized_keys
        git clone https://github.com/RENCI-NRIG/ahab.git
        cd ahab
        mvn install

        echo Git SDX
        cd $WORKING_DIR
        git clone --single-branch -b master https://github.com/RENCI-NRIG/CICI-SAFE.git
        echo Build SDX
        cd ${WORKING_DIR}/CICI-SAFE/exoplex
        mvn  clean package appassembler:assemble -DskipTests

## 4. create an Exogeni slice for SDX.

Modify ${WORKING_DIR}/CICI-SAFE/SDX-Simple/config/sdx.conf.

        cd ${WORKING_DIR}/CICI-SAFE/exoplex
        ./scripts/createslice.sh -c config/sdx.conf

## 5. start SDX server

        cd ${WORKING_DIR}/CICI-SAFE/SDX-Simple
        ./scripts/sdxserver.sh -c config/sdx.conf


# Deploy authorities on VM3
Authorities makes delegations to the client Key

## 1. runs a safeserver for authroties on VM 3

        sudo docker pull yaoyj11/safeserver-v7
        sudo docker run -i -t -d -p 7777:7777 -h safe --name safe ${SAFEIMG}
        sudo docker exec -itd safe /bin/bash -c  "cd /root/safe;sed -i 's/RIAKSERVER/$riak_ip/g' safe-server/src/main/resources/application.conf;./${SAFE_SCRIPT}"

## 2. make delegations to a client: exogeni slice authorization, ip allocation, tag delegation

        SAFE_SERVER=localhost
        USERKEYHASH="QMgHfVKlXfen-tw3JIPAI3eNO_m6bio6IyYep9zM3QE="
        USERSLICE=client2
        USERIP="192.168.20.1/24"
        USERTAG="tag0"
        BIN_DIR=~/CICI-SAFE/exoplex/target/appassembler/bin
        ${BIN_DIR}/AuthorityMock auth ${USERKEYHASH} ${USERSLICE} ${USERIP} ${USERTAG} ${SAFE_SERVER}

# Deploy SDX client on VM4

## 1. share the same riak server as the SDX

## 2. run safe-server on VM4

        sudo docker pull yaoyj11/safeserver-v7
        sudo docker run -i -t -d -p 7777:7777 -h safe --name safe ${SAFEIMG}
        sudo docker exec -itd safe /bin/bash -c  "cd /root/safe;sed -i 's/RIAKSERVER/$riak_ip/g' safe-server/src/main/resources/application.conf;./${SAFE_SCRIPT}"

## 3. generate safe key-pair for client

        SAFE_KEYPAIR="bob"
        SAFE_SERVER=localhost
        sudo docker exec -itd safe /bin/bash /root/safe_keygen.sh ${SAFE_KEYPAIR} /root/safe/safe-server/src/main/resources/prdnsmall
        res=$(curl http://localhost:7777/whoami \
          -H "Cntent-Type:application/json" \
          -d '{"principal":"'${SAFE_KEYPAIR}'","methodParams":[]}'
          )

        echo $res
        principalId=$(echo $res | cut -d"'" -f 2)
        echo principalId for ${SAFE_KEYPAIR} is $principalId

## 4. init safe sets for the new keypair, post policies

        echo ${principalId}
        WORKING_DIR=~
        TAGACL='tag0'
        BIN_DIR=${WORKING_DIR}/CICI-SAFE/exoplex/target/appassembler/bin
        ${BIN_DIR}/AuthorityMock init ${principalId} $TAGACL ${SAFE_SERVER}

## 5. Ask the authorities to make delegations to the client. After that, save the delegation tokens in the user's safe sets. (Copy and run the output commands from the authority delegation, 5 commands in total)

        ${BIN_DIR}/AuthorityMock update ${principalId} passDelegation P00xfQR3bdW649Ti6dCIrFboDKaZz4uDEzjXL_nsngQ= SF5x9ObjJWzzTBIn0aachXlIEbcOq7hkJdjbJuyoLfA=:project1 ${SAFE_SERVER}

## 6. Create client slice

        ${BIN_DIR}/SafeSdxClientSliceServer -c client-config/c0.conf

## 7. stitch to sdx

        sudo ${BIN_DIR}/SafeSdxExogeniClient -c client-config/c0.conf -e 'stitch CNode1 192.168.10.2 192.168.10.1/24'

## 8. client advertise prefix

        sudo ${BIN_DIR}/SafeSdxExogeniClient -c client-config/c0.conf -e 'route 192.168.10.1/24 192.168.10.2'

## 9. both client request for connection [optional]

        sudo ${BIN_DIR}/SafeSdxExogeniClient -c client-config/c0.conf -e 'link 192.168.10.1/24 192.168.20.1/24'

# Stitch and Connect to Chameleon

## 1. create a network and launch a VM in Chameleon cloud. 
   Follow the steps in this video till step 1c, (https://www.youtube.com/watch?v=1fvEEG1iFEI). After creating the network, we will get a directStitch vlan tag (for example, 3298).

## 2. make safe authorization preparations for Chameleon network. 
   The steps are the same as those for ExoGENI client slices, except that we can choose a fake slice name.

### 2a. create safe keypair for chameleon user
### 2b. init safe sets for the new keypair, post policies
### 2c. Ask the authorities to make delegations to the client. After that, save the delegation tokens in the user's safe sets

## 3. run SDX stitchport client to request for stitching to SDX slice. 
   List of stitchports for ExoGENI is available here, (https://wiki.exogeni.net/doku.php?id=public:experimenters:resource_types:start#stitch_port_identifiers). The parameters in the command are "stitch stitchportURL VLAN IP_Chameleon_VM IP_SDX SDX_SITE_NAME OPTIONAL_SDX_NODE"

        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c1.conf -e "stitch http://geni-orca.renci.org/owl/ion.rdf#AL2S/Chameleon/Cisco/6509/GigabitEthernet/1/1 3298 192.168.100.11 192.168.100.1/24 [SDX_SITE_NAME] [STITCH_POINT, e.g., c3]"

## 4. run SDX stitchport client to advertise prefix to SDX

        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c1.conf -e "route 192.168.100.1/24 192.168.100.11"

## 5. run SDX stitchport client to request for connection to another network, (request from the peer is also necessary)

        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c1.conf -e "link 192.168.100.1/24 192.168.10.1/24"


# Configure routing with Quagga on client nodes
   We can configure routin on client nodes with quagga/zebra.

## Install Quagga

        sudo apt install -y quagga

## Enable zebra

        sudo echo "zebra=yes" >> /etc/quagga/daemons

## Set up routing with zebra

        # Assume that the gateway IP address is 192.168.10.1
        sudo echo "ip route 192.168.0.0/16 192.168.10.1"

        # In old versions of Quaaga, this could be "service quagga restart"
        sudo service zebra restart

        sudo ${BIN_DIR}/SafeSdxExogeniClient -c client-config/c0.conf -e 'link 192.168.10.1/24 192.168.20.1/24'
