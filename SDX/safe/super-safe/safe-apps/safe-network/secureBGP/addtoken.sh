#!/bin/bash

echo "updatePathSet: AS0: as1"
curl  -v -X POST http://152.3.136.36:7777/addPathToken -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"oDUSWPyR3ROYX7UwA_j9a0Jq8RWeNH0wypKFzYLRo7M\",\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\"] }"
echo "updatePathSet: as0 : as1/as2"
curl  -v -X POST http://152.3.136.36:7777/addPathToken -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"HxNoaUCrBmiCq4-WWdTJwZJOeBv9LeE5CEQ9g4NoP60\",\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8/GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\"] }"
echo "updatePathSet: as1: as2"
curl  -v -X POST http://152.3.136.36:7777/addPathToken -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"V-wxLxwuSqZxKLLLgo1zx36zX0Zd2KahbTf5idEH4K8\",\"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\"] }"
