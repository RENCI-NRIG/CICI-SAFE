#!/bin/sh

SAFESERVER_SDX=128.194.6.132
SAFESERVER_ALICE=128.194.6.132
SAFESERVER_BOB=128.194.6.132
echo "Policy"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postStitchPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postStitchPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"alice\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postStitchPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postACLPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postACLPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"alice\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postACLPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postConnectivityPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postConnectivityPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"alice\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postConnectivityPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [] }"
#endorsePA <- principal authority
echo "carrier endorse pa"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postEndorsePA -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [\"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\"] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postEndorsePA -H "Content-Type: application/json" -d "{ \"principal\": \"alice\", \"otherValues\": [\"V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8\"] }"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postEndorsePA -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [\"UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA\"] }"

#stitch request
#curl  -v -X POST http://$SAFESERVER:7777/postStitchRequest -H "Content-Type: application/json" -d "{ \"principal\": \"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\", \"otherValues\": [\"alice\",\"b688e2db-84a6-4148-9531-a2f588d733f4\",\"server\",\"c0\"] }"
#curl  -v -X POST http://$SAFESERVER:7777/postStitchRequest -H "Content-Type: application/json" -d "{ \"principal\": \"6MK8qmGNcNSUiuhGskUWs689KNANR2sMXA1fMgcNbNQ\", \"otherValues\": [\"bob\",\"e80c5d87-286d-4a72-8c4f-41451c10f5ba\",\"server\",\"c3\"] }"

#delegate IP prefix
curl  -v -X POST http://$SAFESERVER_SDX:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [\"V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8\",\"192.168.10.1/24\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [\"UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA\",\"192.168.20.1/24\"] }"

#endorsePM <- project member
echo "pa endorse alice"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8\"] }"
echo "pa endorse bob"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E\"] }"
