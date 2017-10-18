echo "IDSet"
#sdx: bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E
#pa: weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24
#bob: UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA
#rpkiroot: iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8
#alice: V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8
#key_p6: KXwvxF_rWupThUEAKwmkMTuhV8X-hqZXOAtMkWBFapc


SAFESERVER_SDX=$1

curl  -v -X POST http://$SAFESERVER_SDX:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [\"sdx\"] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [\"rpkiroot\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"rpkiroot\", \"otherValues\": [] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [\"pa\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"pa\", \"otherValues\": [] }"

echo "Policy"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postStitchPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postConnectivityPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postOwnPrefixPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [] }"
echo "carrier endorse pa"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postEndorsePA -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [\"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\"] }"
echo "update subject set of sdx"
curl  -v -X POST http://$SAFESERVER_SDX:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [\"fmK3VtqBFS6vhpxEZjgABOf-ATBDNdtgiOckdiOTmPg\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [\"f7qb7S-i7x57SClCEyl_QGujmOWTd6BeWBRQbXQxcII\"] }"
