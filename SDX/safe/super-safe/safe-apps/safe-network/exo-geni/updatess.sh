SAFESERVER=152.54.14.38
echo "update subject set of alice and bob"
curl  -v -X POST http://$SAFESERVER:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\", \"otherValues\": [\"$1\"] }"
curl  -v -X POST http://$SAFESERVER:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8\", \"otherValues\": [\"$1\"] }"
curl  -v -X POST http://$SAFESERVER:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E\", \"otherValues\": [\"$1\"] }"
