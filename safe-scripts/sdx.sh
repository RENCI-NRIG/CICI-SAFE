echo "IDSet"
#sdx: bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E
#pa: weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24
#bob: UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA
#rpkiroot: iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8
#alice: V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8
#key_p6: KXwvxF_rWupThUEAKwmkMTuhV8X-hqZXOAtMkWBFapc


SAFESERVER_SDX=128.194.6.137

curl  -v -X POST http://$SAFESERVER_SDX:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [\"sdx\"] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [\"rpkiroot\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"pa\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [] }"

echo "Policy"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postStitchPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postACLPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postConnectivityPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postOwnPrefixPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [] }"
echo "carrier endorse pa"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postEndorsePA -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [\"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\"] }"
#delegate IP prefix
curl  -v -X POST http://$SAFESERVER_SDX:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [\"V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8\",\"192.168.10.1/24\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [\"UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA\",\"192.168.20.1/24\"] }"
#endorsePM <- project member
echo "pa endorse alice"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8\"] }"
echo "pa endorse bob"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E\"] }"
#endorsePM <- project member
echo "update subject set of alice and bob"
curl  -v -X POST http://$SAFESERVER_SDX:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [\"HLSqBJpzAnD42qqmwI2qioNHo_7RDheKy_gS5_mAmM4\"] }"
