#sdx: bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E
#pa: weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24
#bob: UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA
#rpkiroot: iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8
#alice: V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8
#carrot: KXwvxF_rWupThUEAKwmkMTuhV8X-hqZXOAtMkWBFapc

SAFESERVER_CARROT=$1

curl  -v -X POST http://$SAFESERVER_CARROT:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"carrot\", \"otherValues\": [\"carrot\"] }"

curl  -v -X POST http://$SAFESERVER_CARROT:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"carrot\", \"otherValues\": [] }"

#Alice post her access control policy, comment it out if we don't allow carrot to talk to any other
curl  -v -X POST http://$SAFESERVER_CARROT:7777/postACLPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"carrot\", \"otherValues\": [] }"

#carrot endorse a project authority
curl  -v -X POST http://$SAFESERVER_CARROT:7777/postEndorsePA -H "Content-Type: application/json" -d "{ \"principal\": \"carrot\", \"otherValues\": [\"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\"] }"


echo "update subject set of carrot"

#Alice put the tocken for ip delegation and project membership delegation in her subject set
curl  -v -X POST http://$SAFESERVER_CARROT:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"carrot\", \"otherValues\": [\"fmK3VtqBFS6vhpxEZjgABOf-ATBDNdtgiOckdiOTmPg\"] }"

curl  -v -X POST http://$SAFESERVER_CARROT:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"carrot\", \"otherValues\": [\"f7qb7S-i7x57SClCEyl_QGujmOWTd6BeWBRQbXQxcII\"] }"
