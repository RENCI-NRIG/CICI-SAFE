#!/bin/bash

SAFE_SERVER=192.1.3.0 

curl http://${SAFE_SERVER}:7777/postObjectAcl -d "{ \"principal\": \"bob\",  \"otherValues\": [\"bob:object1\", \"git://github.com/jerryz920/spark\"] }"
