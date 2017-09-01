#sdx: bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E
#pa: weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24
#bob: UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA
#rpkiroot: iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8
#alice: V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8
#key_p6: KXwvxF_rWupThUEAKwmkMTuhV8X-hqZXOAtMkWBFapc

SAFESERVER_BOB=$1

curl  -v -X POST http://$SAFESERVER_BOB:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [\"bob\"] }"

curl  -v -X POST http://$SAFESERVER_BOB:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [] }"


#Bob post her access control policy, comment it out if we don't allow alice to talk to any other
curl  -v -X POST http://$SAFESERVER_BOB:7777/postACLPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [] }"

#Bob endorse a project authority
curl  -v -X POST http://$SAFESERVER_BOB:7777/postEndorsePA -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [\"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\"] }"

#Bob put the tocken for ip delegation and project membership delegation in her subject set
curl -v -X POST http://$SAFESERVER_BOB:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [\"SV51XV5zIlONA255wethUSJRNTxG8Lh-kaYP9PZEOxU\"] }"

curl -v -X POST http://$SAFESERVER_BOB:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [\"HLSqBJpzAnD42qqmwI2qioNHo_7RDheKy_gS5_mAmM4\"] }"
