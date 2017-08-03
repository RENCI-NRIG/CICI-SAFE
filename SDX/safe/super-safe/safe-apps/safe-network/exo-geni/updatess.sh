
SAFESERVER_SDX=128.194.6.132
SAFESERVER_ALICE=128.194.6.132
SAFESERVER_BOB=128.194.6.132
#delegate IP prefix
curl  -v -X POST http://$SAFESERVER_ALICE:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"alice\", \"otherValues\": [\"SV51XV5zIlONA255wethUSJRNTxG8Lh-kaYP9PZEOxU\"] }"
curl -v -X POST http://$SAFESERVER_BOB:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [\"SV51XV5zIlONA255wethUSJRNTxG8Lh-kaYP9PZEOxU\"] }"

#endorsePM <- project member
echo "update subject set of alice and bob"
curl  -v -X POST http://$SAFESERVER_SDX:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"sdx\", \"otherValues\": [\"HLSqBJpzAnD42qqmwI2qioNHo_7RDheKy_gS5_mAmM4\"] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"alice\", \"otherValues\": [\"HLSqBJpzAnD42qqmwI2qioNHo_7RDheKy_gS5_mAmM4\"] }"
curl -v -X POST http://$SAFESERVER_BOB:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"bob\", \"otherValues\": [\"HLSqBJpzAnD42qqmwI2qioNHo_7RDheKy_gS5_mAmM4\"] }"
