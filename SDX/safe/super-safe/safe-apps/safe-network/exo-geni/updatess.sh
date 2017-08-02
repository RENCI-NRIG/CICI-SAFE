SAFESERVER_SDX=152.54.14.33
SAFESERVER_ALICE=152.54.14.40
SAFESERVER_BOB=152.54.14.60

#delegate IP prefix
curl  -v -X POST http://$SAFESERVER_ALICE:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\", \"otherValues\": [\"I2-tYwCMsp4O7NjCzownA9zg9-fCWsnfRyulqL4icFI\"] }"
curl -v -X POST http://$SAFESERVER_BOB:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"6MK8qmGNcNSUiuhGskUWs689KNANR2sMXA1fMgcNbNQ\", \"otherValues\": [\"I2-tYwCMsp4O7NjCzownA9zg9-fCWsnfRyulqL4icFI\"] }"

#endorsePM <- project member
echo "update subject set of alice and bob"
curl  -v -X POST http://$SAFESERVER_SDX:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"XDIA1RQ6jrJq2Z-SD321s3MeeWnqwCKWE9LdrRnhJ5g\", \"otherValues\": [\"os5dQIDVIEkkT1eLZM-6UxVJ8k_tf-zVkuLjMxXe89Q\"] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\", \"otherValues\": [\"os5dQIDVIEkkT1eLZM-6UxVJ8k_tf-zVkuLjMxXe89Q\"] }"
curl -v -X POST http://$SAFESERVER_BOB:7777/updateSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"6MK8qmGNcNSUiuhGskUWs689KNANR2sMXA1fMgcNbNQ\", \"otherValues\": [\"os5dQIDVIEkkT1eLZM-6UxVJ8k_tf-zVkuLjMxXe89Q\"] }"