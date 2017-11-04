echo "IDSet"
#sdx: bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E
#pa: weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24
#bob: UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA
#rpkiroot: iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8
#alice: V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8
#carrot: KXwvxF_rWupThUEAKwmkMTuhV8X-hqZXOAtMkWBFapc


SAFESERVER_TRUSTED=$1


curl  -v -X POST http://$SAFESERVER_TRUSTED:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [\"rpkiroot\"] }"
curl  -v -X POST http://$SAFESERVER_TRUSTED:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [] }"

curl  -v -X POST http://$SAFESERVER_TRUSTED:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"pa\"] }"
curl  -v -X POST http://$SAFESERVER_TRUSTED:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [] }"

#delegate IP prefix
curl  -v -X POST http://$SAFESERVER_TRUSTED:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [\"V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8\",\"192.168.10.1/24\"] }"
curl  -v -X POST http://$SAFESERVER_TRUSTED:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [\"UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA\",\"192.168.20.1/24\"] }"
curl  -v -X POST http://$SAFESERVER_TRUSTED:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [\"KXwvxF_rWupThUEAKwmkMTuhV8X-hqZXOAtMkWBFapc\",\"10.32.98.1/24\"] }"
#endorsePM <- project member
echo "pa endorse alice"
curl  -v -X POST http://$SAFESERVER_TRUSTED:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8\"] }"
echo "pa endorse bob"
curl  -v -X POST http://$SAFESERVER_TRUSTED:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA\"] }"
echo "pa endorse sdx"
curl  -v -X POST http://$SAFESERVER_TRUSTED:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E\"] }"
echo "pa endorse carrot"
curl  -v -X POST http://$SAFESERVER_TRUSTED:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"KXwvxF_rWupThUEAKwmkMTuhV8X-hqZXOAtMkWBFapc\"] }"
