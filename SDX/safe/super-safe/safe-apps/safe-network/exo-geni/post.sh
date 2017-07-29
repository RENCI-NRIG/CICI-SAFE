#!/bin/sh
#key_p3: bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E sdx
#key_p2: weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24 alice
#key_p1: iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8 bob
#key_p4: V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8 PA
#key_p5: UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA rpkiroot

#start server:
#sbt  "project safe-server" "run -f /home/yaoyj11/project/integration/safe-apps/safe-network/routing/routing.slang  -r safeService  -kd   src/main/resources/key"

#check cert:
#curl http://152.3.145.15:8098/buckets/keys/FPd8BaxV0qgOSDpccAJ3XTRL8BLcoL_aj3s84yDQCmg

#postIDSet
#echo "init set"
#curl  -v -X POST http://$SAFESERVER:7777/postInitNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E\", \"otherValues\": [] }"
SAFESERVER=128.194.6.154

echo "Policy"
curl  -v -X POST http://$SAFESERVER:7777/postStitchPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER:7777/postStitchPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER:7777/postStitchPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8\", \"otherValues\": [] }"

curl  -v -X POST http://$SAFESERVER:7777/postACLPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER:7777/postACLPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER:7777/postACLPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8\", \"otherValues\": [] }"

curl  -v -X POST http://$SAFESERVER:7777/postConnectivityPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER:7777/postConnectivityPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER:7777/postConnectivityPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8\", \"otherValues\": [] }"
#endorsePA
echo "carrier endorse pa"
curl  -v -X POST http://$SAFESERVER:7777/postEndorsePA -H "Content-Type: application/json" -d "{ \"principal\": \"bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E\", \"otherValues\": [\"V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8\"] }"
curl  -v -X POST http://$SAFESERVER:7777/postEndorsePA -H "Content-Type: application/json" -d "{ \"principal\": \"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\", \"otherValues\": [\"V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8\"] }"
curl  -v -X POST http://$SAFESERVER:7777/postEndorsePA -H "Content-Type: application/json" -d "{ \"principal\": \"iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8\", \"otherValues\": [\"V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8\"] }"

#stitch request
#curl  -v -X POST http://$SAFESERVER:7777/postStitchRequest -H "Content-Type: application/json" -d "{ \"principal\": \"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\", \"otherValues\": [\"alice\",\"b688e2db-84a6-4148-9531-a2f588d733f4\",\"server\",\"c0\"] }"
#curl  -v -X POST http://$SAFESERVER:7777/postStitchRequest -H "Content-Type: application/json" -d "{ \"principal\": \"iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8\", \"otherValues\": [\"bob\",\"e80c5d87-286d-4a72-8c4f-41451c10f5ba\",\"server\",\"c3\"] }"

#delegate IP prefix
curl  -v -X POST http://$SAFESERVER:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA\", \"otherValues\": [\"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\",\"192.168.10.1/24\"] }"
curl  -v -X POST http://$SAFESERVER:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA\", \"otherValues\": [\"iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8\",\"192.168.20.1/24\"] }"

#endorsePM
echo "pa endorse alice"
curl  -v -X POST http://$SAFESERVER:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8\", \"otherValues\": [\"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\"] }"
echo "pa endorse bob"
curl  -v -X POST http://$SAFESERVER:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8\", \"otherValues\": [\"iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8\"] }"
curl  -v -X POST http://$SAFESERVER:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8\", \"otherValues\": [\"bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E\"] }"





#echo "update subject set of alice and bob"
#curl  -v -X POST http://$SAFESERVER:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\", \"otherValues\": [\"IIxSVyZ-p7aQpAfaP9x3eXecVwDEvb7fE5PnNyAo6tY\"] }"
##
#curl  -v -X POST http://$SAFESERVER:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8\", \"otherValues\": [\"IIxSVyZ-p7aQpAfaP9x3eXecVwDEvb7fE5PnNyAo6tY\"] }"
