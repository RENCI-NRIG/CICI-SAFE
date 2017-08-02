echo "IDSet"

SAFESERVER_SDX=152.54.14.33
SAFESERVER_ALICE=152.54.14.40
SAFESERVER_BOB=152.54.14.60

curl  -v -X POST http://$SAFESERVER_SDX:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"XDIA1RQ6jrJq2Z-SD321s3MeeWnqwCKWE9LdrRnhJ5g\", \"otherValues\": [\"carrier\"] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\", \"otherValues\": [\"alice\"] }"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"6MK8qmGNcNSUiuhGskUWs689KNANR2sMXA1fMgcNbNQ\", \"otherValues\": [\"bob\"] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"XDIA1RQ6jrJq2Z-SD321s3MeeWnqwCKWE9LdrRnhJ5g\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"6MK8qmGNcNSUiuhGskUWs689KNANR2sMXA1fMgcNbNQ\", \"otherValues\": [] }"


curl  -v -X POST http://$SAFESERVER_SDX:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"Y3it9CtE1e0RVQ3cskjZ0Oys0V9Tj6hs5iQYNP75KVA\", \"otherValues\": [\"carrier\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"Y3it9CtE1e0RVQ3cskjZ0Oys0V9Tj6hs5iQYNP75KVA\", \"otherValues\": [] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postIdSet -H "Content-Type: application/json" -d "{ \"principal\": \"5hgyW8w4NxcRFDfhOt74INq7CmnSGHyzdfyxsH1US6U\", \"otherValues\": [\"carrier\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postSubjectSet -H "Content-Type: application/json" -d "{ \"principal\": \"5hgyW8w4NxcRFDfhOt74INq7CmnSGHyzdfyxsH1US6U\", \"otherValues\": [] }"