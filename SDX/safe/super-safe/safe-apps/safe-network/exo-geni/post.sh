#!/bin/sh
SAFESERVER_SDX=152.54.14.33
SAFESERVER_ALICE=152.54.14.40
SAFESERVER_BOB=152.54.14.60

echo "Policy"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postStitchPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"XDIA1RQ6jrJq2Z-SD321s3MeeWnqwCKWE9LdrRnhJ5g\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postStitchPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postStitchPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"6MK8qmGNcNSUiuhGskUWs689KNANR2sMXA1fMgcNbNQ\", \"otherValues\": [] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postACLPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"XDIA1RQ6jrJq2Z-SD321s3MeeWnqwCKWE9LdrRnhJ5g\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postACLPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postACLPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"6MK8qmGNcNSUiuhGskUWs689KNANR2sMXA1fMgcNbNQ\", \"otherValues\": [] }"

curl  -v -X POST http://$SAFESERVER_SDX:7777/postConnectivityPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"XDIA1RQ6jrJq2Z-SD321s3MeeWnqwCKWE9LdrRnhJ5g\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postConnectivityPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\", \"otherValues\": [] }"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postConnectivityPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"6MK8qmGNcNSUiuhGskUWs689KNANR2sMXA1fMgcNbNQ\", \"otherValues\": [] }"
#endorsePA <- principal authority
echo "carrier endorse pa"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postEndorsePA -H "Content-Type: application/json" -d "{ \"principal\": \"XDIA1RQ6jrJq2Z-SD321s3MeeWnqwCKWE9LdrRnhJ5g\", \"otherValues\": [\"5hgyW8w4NxcRFDfhOt74INq7CmnSGHyzdfyxsH1US6U\"] }"
curl  -v -X POST http://$SAFESERVER_ALICE:7777/postEndorsePA -H "Content-Type: application/json" -d "{ \"principal\": \"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\", \"otherValues\": [\"5hgyW8w4NxcRFDfhOt74INq7CmnSGHyzdfyxsH1US6U\"] }"
curl  -v -X POST http://$SAFESERVER_BOB:7777/postEndorsePA -H "Content-Type: application/json" -d "{ \"principal\": \"6MK8qmGNcNSUiuhGskUWs689KNANR2sMXA1fMgcNbNQ\", \"otherValues\": [\"5hgyW8w4NxcRFDfhOt74INq7CmnSGHyzdfyxsH1US6U\"] }"

#stitch request
#curl  -v -X POST http://$SAFESERVER:7777/postStitchRequest -H "Content-Type: application/json" -d "{ \"principal\": \"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\", \"otherValues\": [\"alice\",\"b688e2db-84a6-4148-9531-a2f588d733f4\",\"server\",\"c0\"] }"
#curl  -v -X POST http://$SAFESERVER:7777/postStitchRequest -H "Content-Type: application/json" -d "{ \"principal\": \"6MK8qmGNcNSUiuhGskUWs689KNANR2sMXA1fMgcNbNQ\", \"otherValues\": [\"bob\",\"e80c5d87-286d-4a72-8c4f-41451c10f5ba\",\"server\",\"c3\"] }"

#delegate IP prefix
curl  -v -X POST http://$SAFESERVER_SDX:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"Y3it9CtE1e0RVQ3cskjZ0Oys0V9Tj6hs5iQYNP75KVA\", \"otherValues\": [\"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\",\"192.168.10.1/24\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"Y3it9CtE1e0RVQ3cskjZ0Oys0V9Tj6hs5iQYNP75KVA\", \"otherValues\": [\"6MK8qmGNcNSUiuhGskUWs689KNANR2sMXA1fMgcNbNQ\",\"192.168.20.1/24\"] }"

#endorsePM <- project member
echo "pa endorse alice"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"5hgyW8w4NxcRFDfhOt74INq7CmnSGHyzdfyxsH1US6U\", \"otherValues\": [\"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\"] }"
echo "pa endorse bob"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"5hgyW8w4NxcRFDfhOt74INq7CmnSGHyzdfyxsH1US6U\", \"otherValues\": [\"6MK8qmGNcNSUiuhGskUWs689KNANR2sMXA1fMgcNbNQ\"] }"
curl  -v -X POST http://$SAFESERVER_SDX:7777/postEndorsePM -H "Content-Type: application/json" -d "{ \"principal\": \"5hgyW8w4NxcRFDfhOt74INq7CmnSGHyzdfyxsH1US6U\", \"otherValues\": [\"XDIA1RQ6jrJq2Z-SD321s3MeeWnqwCKWE9LdrRnhJ5g\"] }"
