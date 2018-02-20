#!/bin/bash

SAFE_IP=`hostname -i` 

curl http://${SAFE_IP}:7777/postObjectAcl -d "{ \"principal\": \"bob\",  \"otherValues\": [\"bob:object1\", \"git://github.com/jerryz920/spark\"] }"
