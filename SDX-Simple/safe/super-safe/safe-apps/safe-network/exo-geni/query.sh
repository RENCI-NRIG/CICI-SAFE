#!/bin/sh

curl  http://152.3.136.36:7777/verifyStitch -H "Content-Type: application/json" -d "{ \"principal\": \"XDIA1RQ6jrJq2Z-SD321s3MeeWnqwCKWE9LdrRnhJ5g\", \"otherValues\": [\"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\",\"client\",\"4db2b812-39ac-4333-9417-07adef946e68\",\"server\",\"c0\"] }"
curl  http://152.3.136.36:7777/connectivity -H "Content-Type: application/json" -d "{ \"principal\": \"XDIA1RQ6jrJq2Z-SD321s3MeeWnqwCKWE9LdrRnhJ5g\", \"otherValues\": [\"Iq7mxtcMBj5PK8mn4h1gR8BYkwOkoonUiEC_-dWxgAU\",\"6MK8qmGNcNSUiuhGskUWs689KNANR2sMXA1fMgcNbNQ\",\"192.168.10.1/24\",\"192.168.20.1/24\"] }"
