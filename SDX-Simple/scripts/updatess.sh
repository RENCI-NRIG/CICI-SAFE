#!/bin/bash

if [ "$#" -ne 1 ]; then
   echo "illegal number of parameters"
   echo "usage: "$0" SafeServerIP"
   exit 1
fi
SAFESERVER_IP=$1
#endorsePM <- project member
echo "update subject set of alice and bob"
curl  -v -X POST http://$SAFESERVER_IP:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [\"HLSqBJpzAnD42qqmwI2qioNHo_7RDheKy_gS5_mAmM4\"] }"
