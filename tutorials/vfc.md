# Run SDX on VFC in Chameleon

In this demo, we run SDX on VFC in Chameleon and connect client networks on Chameleon and ExoGENI with SDX. The video is available here, https://youtu.be/lKLTVAnOG74.

## 1. VFC with ExoGENI-stitchable networks

Run SDN controller,

        ryu-manager --ofp-tcp-listen-port 6653 ~/CICI-SAFE/ryu-apps/vfc_router.py ~/CICI-SAFE/ryu-apps/ofctl_rest.py

Or run SDN controller in docker

        PLEXUSIMG="yaoyj11/plexus-v3"
        sudo docker pull ${PLEXUSIMG}
        sudo docker run -i -t -d -p 8080:8080 -p 6653:6653 -p 3000:3000 -h plexus --name plexus ${PLEXUSIMG}
        sudo docker exec -itd plexus /bin/bash -c  "cd /root;pkill ryu-manager; ryu-manager --ofp-tcp-listen-port 6653 --log-file ~/ryu.log --default-log-level 1 ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/vfc_router.py ryu/ryu/app/ofctl_rest.py"


If necessary, delete all flows on the VFC and restart SDN controller

        curl -X DELETE http://198.129.50.26:8080/stats/flowentry/clear/196776737624907
        curl -X DELETE http://198.129.50.26:8080/stats/flowentry/clear/279779748132173



## 2. Save topology of the VFC in json file (exoplex/vfc-config/topo.json).
Key words are "router", "link" and "stitch". "router" represents the OpenFlow-Enabled vfc, "link" represents the link between two VFCs (ExoGENI circuits), and "stitch" represents ExoGENI switchable network on the VFC.


## 3. Start vfc server

        ./scripts/vfcserver.sh -c vfc-config/vfc.conf

## 4. Create ExoGENI client networks or Chameleon client networks
To create ExoGENI client network
        
        ./scripts/createclientslice.sh -c client-config/alice.conf

## 5. Stitch client network to VFC
        
### Stitch ExoGENI client slice to VFC

        ./scripts/sdx_exogeni_client.sh -c client-config/sc1.conf -e "stitchvfc CNode1 UC 3298 192.168.10.2 192.168.10.1/24"

        ./scripts/sdx_exogeni_client.sh -c client-config/sc2.conf -e "stitchvfc CNode1 TACC 3502 192.168.20.2 192.168.20.1/24"

### Stitch Chameleon network to VFC
        
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c1.conf -e "stitchvfc TACC 2085 192.168.100.17 192.168.100.1/24"

        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c2.conf -e "stitchvfc UC 3022 192.168.200.17 192.168.200.1/24"

## 6. Client networks advertise prefix

### ExoGENI client

        ./scripts/sdx_exogeni_client.sh -c client-config/sc1.conf -e "route 192.168.10.1/24 192.168.10.2"

        ./scripts/sdx_exogeni_client.sh -c client-config/sc2.conf -e "route 192.168.20.1/24 192.168.20.2"

### Chameleon client

        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c1.conf -e "route  192.168.100.1/24 192.168.100.17"

        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c2.conf -e "route  192.168.200.1/24 192.168.200.17"

## 7. Client networks request for connection

### ExoGENI client

        ./scripts/sdx_exogeni_client.sh -c client-config/sc1.conf -e "link 192.168.10.1/24 192.168.20.1/24"
        ./scripts/sdx_exogeni_client.sh -c client-config/sc1.conf -e "link 192.168.10.1/24 192.168.100.1/24"
        ./scripts/sdx_exogeni_client.sh -c client-config/sc1.conf -e "link 192.168.10.1/24 192.168.200.1/24"

        ./scripts/sdx_exogeni_client.sh -c client-config/sc2.conf -e "link 192.168.20.1/24 192.168.10.1/24"
        ./scripts/sdx_exogeni_client.sh -c client-config/sc2.conf -e "link 192.168.20.1/24 192.168.100.1/24"
        ./scripts/sdx_exogeni_client.sh -c client-config/sc2.conf -e "link 192.168.20.1/24 192.168.200.1/24"

### Chameleon client

        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c2.conf -e "link  192.168.200.1/24 192.168.100.1/24"
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c2.conf -e "link  192.168.200.1/24 192.168.10.1/24"
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c2.conf -e "link  192.168.200.1/24 192.168.20.1/24"

        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c1.conf -e "link  192.168.100.1/24 192.168.200.1/24"
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c1.conf -e "link  192.168.100.1/24 192.168.20.1/24"
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c1.conf -e "link  192.168.100.1/24 192.168.10.1/24"



# Deploy SDX for VFC
The process of deploying SDX for VFC is similar to the process of deploying ExoGENI-based SDX. The SAFE authorization related steps are exactly the same. 

Install prerequisites

        sudo apt install -y maven openjdk-8-jdk docker.io

Generate ssh key (the default key pair created by ssh-keygen command  without options doesn't work on latest Ubuntu)

        ssh-keygen -t rsa -m PEM

Set the version and safe docker image, plexus sdn controller docker image and safe server script as environment variable. Set the public ip addresses of riak server and SDX controller. Run this in all nodes.

        SAFEIMG="yaoyj11/safeserver-v8"
        PLEXUSIMG="yaoyj11/plexus-v3"
        SAFE_SCRIPT="sdx-routing.sh"
        riak_ip="128.194.6.235"
        sdx_ip="128.194.6.161"

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
The SDN controller of the switch on the VFC is fixed. 

        publicurl="http://${sdx_ip}:8888"
        sudo docker pull ${PLEXUSIMG}
        sudo docker run -i -t -d -p 8080:8080 -p 6653:6653 -p 3000:3000 -h plexus --name plexus ${PLEXUSIMG}
        sudo docker exec -itd plexus /bin/bash -c  "cd /root;pkill ryu-manager; ryu-manager --ofp-tcp-listen-port 6653 --log-file ~/ryu.log --default-log-level 1 ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/vfc_router.py ryu/ryu/app/ofctl_rest.py --zapi-db-url ${publicurl}/sdx/flow/packetin"

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
        git clone --single-branch -b vfc https://github.com/RENCI-NRIG/CICI-SAFE.git
        echo Build SDX
        cd ${WORKING_DIR}/CICI-SAFE/exoplex
        mvn  clean package appassembler:assemble -DskipTests

## 4. create a VFC on Chameleon with a switch and two ExoGENI-stitchable networks. Save the topology in a Json file (exoplex/vfc-config/topo.json). 
Key words are "router", "link" and "stitch". "router" represents the OpenFlow-Enabled vfc, "link" represents the link between two VFCs (ExoGENI circuits), and "stitch" represents ExoGENI switchable network on the VFC.
The name of the router doesn't matter. The name of the stitch should start with "net".

## 5. start SDX server

        cd ${WORKING_DIR}/CICI-SAFE/exoplex
        ./scripts/vfcserver.sh -c vfc-config/vfc.conf


# Deploy authorities on VM3
Authorities makes delegations to the client Key

## 1. runs a safeserver for authroties on VM 3

        sudo docker pull yaoyj11/safeserver-v7
        sudo docker run -i -t -d -p 7777:7777 -h safe --name safe ${SAFEIMG}
        sudo docker exec -itd safe /bin/bash -c  "cd /root/safe;sed -i 's/RIAKSERVER/$riak_ip/g' safe-server/src/main/resources/application.conf;./${SAFE_SCRIPT}"

## 2. make delegations to a client: client slice authorization, ip allocation, tag delegation

        SAFE_SERVER=localhost
        USERKEYHASH="MfIPn0qnsuGiJtb3xJyAUOB1dBmGw9IJm5-wKUgVOlU="
        USERSLICE=bob
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

## 6. Create client slice, stitch client networks, advertise client IP prefix and request for connection are shown at the beginning of this guide. For client networks, we can do ExoGENI client networks only, Chameleon networks only or a mix of those two kinds of client networks.

