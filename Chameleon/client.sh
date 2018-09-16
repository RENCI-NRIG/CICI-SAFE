#!/bin/bash

#NAME=dave
#SDX_SERVER_IP=192.5.87.61
#SDX_SERVER_PORT=8888
#RESERVATION_ID=07e518ef-4422-4657-bc80-186c98d80402
#NETWORK_NAME=a772a899-ff3d-420b-8b31-1c485092481a
#KEY_NAME=pruth-chameleon

NAME=$1
SDX_SERVER_IP=$2
SDX_SERVER_PORT=8888
RESERVATION_ID=5f3b494c-24ff-477c-962b-35b5558beedb
NETWORK_NAME=a772a899-ff3d-420b-8b31-1c485092481a
KEY_NAME=pruth-chameleon


SDX_SERVER=${SDX_SERVER_IP}:${SDX_SERVER_PORT}
STACK_NAME=pruth-$NAME


openstack stack create --template CICI-SAFE-client.yaml \
--parameter "key_name=${KEY_NAME}" \
--parameter "reservation_id=${RESERVATION_ID}" \
--parameter "network_name=${NETWORK_NAME}" \
--parameter "client_name=${NAME}" \
--parameter "sdx_server=${SDX_SERVER}" \
$STACK_NAME
