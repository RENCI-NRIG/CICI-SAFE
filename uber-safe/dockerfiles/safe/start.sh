#!/bin/bash
# Run SAFE server

cd ~/SAFE/uber-safe

# Set up: configuration the storage server for SAFE
sed -i "/.*url = \"http/ s:.*:    url = \"http\://${RIAK_IP}\:8098/types/safesets/buckets/safe/keys\":" safe-server/src/main/resources/application.conf

# Run
 ~/sbt/bin/sbt "project safe-server" "run -f /root/SAFE/uber-safe/safe-apps/cloud-attestation/latte.slang  -r safeService  -kd   src/main/resources/multi-principal-keys/" 

#bash ~/test.sh
/bin/bash
