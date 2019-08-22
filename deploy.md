# Deploy SDX

Install prerequisites

        sudo apt install -y maven openjdk-8-jdk docker.io

Generate ssh key, (the key pair created by ssh-keygen command doesn't work on latest Ubuntu)

        ssh-keygen -t rsa -m PEM

Set the version and safe docker image, plexus sdn controller docker image and safe server script as environment variable

        SAFEIMG="yaoyj11/safeserver-v8"
        PLEXUSIMG="yaoyj11/plexus-v3"
        SAFE_SCRIPT="sdx-routing.sh"

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
#### For security, plexus controller and safe server should only listen on localhost so that only the SDX controller can calls them.
###  a) deploy safe server

        riak_ip="IP address of VM1"
        sudo docker pull ${SAFEIMG}
        sudo docker run -i -t -d -p 7777:7777 -h safe --name safe ${SAFEIMG}
        sudo docker exec -itd safe /bin/bash -c  "cd /root/safe;sed -i 's/RIAKSERVER/$riak_ip/g' safe-server/src/main/resources/application.conf;./${SAFE_SCRIPT}"

###  b) deploy SDN controller

        public_ip="public ip of SDX controller"
        publicurl="http://${public_ip}:8888"
        sudo docker pull ${PLEXUSIMG}
        sudo docker run -i -t -d -p 8080:8080 -p 6633:6633 -p 3000:3000 -h plexus --name plexus ${PLEXUSIMG}
        sudo docker exec -itd plexus /bin/bash -c  "cd /root;pkill ryu-manager; ryu-manager --log-file ~/ryu.log --default-log-level 1 ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/rest_router.py ryu/ryu/app/ofctl_rest.py --zapi-db-url ${publicurl}/sdx/flow/packetin"

## 3. deploy SDX controller on VM2

        sudo apt install -y maven openjdk-8-jdk
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
        git clone --single-branch -b tridentcom https://github.com/RENCI-NRIG/CICI-SAFE.git
        
        echo Build SDX
        cd ${WORKING_DIR}/CICI-SAFE/SDX-Simple/SAFE_SDX
        mvn  clean package appassembler:assemble

## 4. create an Exogeni slice for SDX.
      
Modify ${WORKING_DIR}/CICI-SAFE/SDX-Simple/config/sdx.conf.

        cd ${WORKING_DIR}/CICI-SAFE/SDX-Simple
        ./scripts/createslice.sh -c config/sdx.conf

## 5. start SDX server

        cd ${WORKING_DIR}/CICI-SAFE/SDX-Simple
        ./scripts/sdxserver.sh -c config/sdx.conf


# Deploy authorities on VM3
#### Authorities makes delegations to the client Key

## 1. runs a safeserver for authroties on VM 3

        riak_ip="IP address of VM1"
        sudo docker pull yaoyj11/safeserver-v7
        sudo docker run -i -t -d -p 7777:7777 -h safe --name safe ${SAFEIMG}
        sudo docker exec -itd safe /bin/bash -c  "cd /root/safe;sed -i 's/RIAKSERVER/$riak_ip/g' safe-server/src/main/resources/application.conf;./${SAFE_SCRIPT}"

## 2. make delegations to a client: exogeni slice authorization, ip allocation, tag delegation

        SAFE_SERVER=localhost
        USERKEYHASH="82fCYt8XXlNAJnDuZZABTcURxz2ZpOwT9IXq6rbSHv0="
        USERSLICE=client1
        USERIP="192.168.10.1/24"
        USERTAG="tag0"
        
        BIN_DIR=~/CICI-SAFE/SDX-Simple/SAFE_SDX/target/appassembler/bin
        ${BIN_DIR}/AuthorityMock auth ${USERKEYHASH} ${USERSLICE} ${USERIP} ${USERTAG} ${SAFE_SERVER}

# Deploy SDX client on VM4

## 1. share the same riak server as the SDX

## 2. run safe-server on VM4

        export riak_ip="IP address of VM1"
        sudo docker pull yaoyj11/safeserver-v7
        sudo docker run -i -t -d -p 7777:7777 -h safe --name safe ${SAFEIMG}
        sudo docker exec -itd safe /bin/bash -c  "cd /root/safe;sed -i 's/RIAKSERVER/$riak_ip/g' safe-server/src/main/resources/application.conf;./${SAFE_SCRIPT}"

## 3. generate safe key-pair for client

        SAFE_KEYPAIR="alice"
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
        BIN_DIR=${WORKING_DIR}/CICI-SAFE/SDX-Simple/SAFE_SDX/target/appassembler/bin
        ${BIN_DIR}/AuthorityMock init ${principalId} $TAGACL ${SAFE_SERVER}

## 5. Ask the authorities to make delegations to the client. After that, copy and paste each line from the output of authority to Params

        PARAMS='updateTagSet c0WrmnifdojXcYv5zS1dCSSHHKY9rbAfHbjllwZxj14= 0_ah37_Nyt8Xgqq1JFHfAD3TA9Mrx0WpLgrRx2w7Dgc=:tag0'
        sudo ${BIN_DIR}/AuthorityMock update ${principalId} ${PARAMS} ${SAFE_SERVER}

## 6. Create client slice

        ${BIN_DIR}/SafeSdxClientSliceServer -c client-config/c0.conf

## 7. stitch to sdx

        sudo ${BIN_DIR}/SafeSdxExogeniClient -c client-config/c0.conf -e 'stitch CNode1 192.168.10.2 192.168.10.1/24'


