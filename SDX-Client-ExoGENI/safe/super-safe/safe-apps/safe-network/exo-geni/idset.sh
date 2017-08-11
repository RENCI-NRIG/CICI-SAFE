echo "IDSet"
sdx: bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E
pa: weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24
bob: UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA
rpkiroot: iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8
alice: V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8
key_p6: KXwvxF_rWupThUEAKwmkMTuhV8X-hqZXOAtMkWBFapc


SAFESERVER_SDX=128.194.6.133
SAFESERVER_ALICE=128.194.6.133
SAFESERVER_BOB=128.194.6.133

curl  -v -X POST http://$SAFESERVER_SDX:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [\"sdx\"] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"alice\", \"otherValues\": [\"alice\"] }"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [\"bob\"] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"alice\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [] }"


curl  -v -X POST http://$SAFESERVER_SDX:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [\"rpkiroot\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"pa\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [] }"
