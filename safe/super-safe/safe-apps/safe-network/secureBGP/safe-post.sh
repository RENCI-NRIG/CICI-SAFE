#!/bin/sh
#as0 iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY
#as1 eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8
#as2 GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA
#as3 t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c
#as4 eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg
#rpkiroot XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU
#start server:
#sbt  "project safe-server" "run -f /home/yaoyj11/project/integration/safe-apps/safe-network/routing/safe-routing.slang  -r safeService  -kd   src/main/resources/key"

#check cert:
#curl http://152.3.145.15:8098/types/safesets/buckets/safe/keys

#add internet Registry
curl  -v -X POST http://152.3.136.36:7777/postInternetRegistry -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\"] }"
curl  -v -X POST http://152.3.136.36:7777/postInternetRegistry -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\"] }"
curl  -v -X POST http://152.3.136.36:7777/postInternetRegistry -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [\"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\"] }"

#postIDSet
#echo "init set"
curl  -v -X POST http://152.3.136.36:7777/postInitNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postInitNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postInitNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postInitNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postInitNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\", \"otherValues\": [] }"
#curl  -v -X POST http://152.3.136.36:7777/postInitNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\", \"otherValues\": [] }"

curl  -v -X POST http://152.3.136.36:7777/postInitCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postInitCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postInitCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postInitCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postInitCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\", \"otherValues\": [] }"
#curl  -v -X POST http://152.3.136.36:7777/postInitCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\", \"otherValues\": [] }"

curl  -v -X POST http://152.3.136.36:7777/postInitAcceptRoute -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postInitAcceptRoute -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postInitAcceptRoute -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postInitAcceptRoute -H "Content-Type: application/json" -d "{ \"principal\": \"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postInitAcceptRoute -H "Content-Type: application/json" -d "{ \"principal\": \"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\", \"otherValues\": [] }"
#curl  -v -X POST http://152.3.136.36:7777/postInitAcceptRoute -H "Content-Type: application/json" -d "{ \"principal\": \"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\", \"otherValues\": [] }"
#postPolicy:
echo "Policy"
curl  -v -X POST http://152.3.136.36:7777/postRoutingPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postRoutingPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postRoutingPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postRoutingPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postRoutingPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\", \"otherValues\": [] }"
#curl  -v -X POST http://152.3.136.36:7777/postRoutingPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\", \"otherValues\": [] }"
#  "message": "[u'FPd8BaxV0qgOSDpccAJ3XTRL8BLcoL_aj3s84yDQCmg']"

echo "ownprefix policy"

curl  -v -X POST http://152.3.136.36:7777/postOwnPrefixPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postOwnPrefixPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postOwnPrefixPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postOwnPrefixPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\", \"otherValues\": [] }"
curl  -v -X POST http://152.3.136.36:7777/postOwnPrefixPolicy -H "Content-Type: application/json" -d "{ \"principal\": \"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\", \"otherValues\": [] }"


#echo "advertise policy"
#curl  -v -X POST http://152.3.136.36:7777/postAdvertisePolicy -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [] }"
#curl  -v -X POST http://152.3.136.36:7777/postAdvertisePolicy -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [] }"
#curl  -v -X POST http://152.3.136.36:7777/postAdvertisePolicy -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [] }"
#curl  -v -X POST http://152.3.136.36:7777/postAdvertisePolicy -H "Content-Type: application/json" -d "{ \"principal\": \"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\", \"otherValues\": [] }"
#curl  -v -X POST http://152.3.136.36:7777/postAdvertisePolicy -H "Content-Type: application/json" -d "{ \"principal\": \"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\", \"otherValues\": [] }"
#curl  -v -X POST http://152.3.136.36:7777/postAdvertisePolicy -H "Content-Type: application/json" -d "{ \"principal\": \"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\", \"otherValues\": [] }"
#  "message": "[u'BGZXsqiu08Mpi5zaZWLr8EPe6_ZYDaOc4qqOuByI43g']"


#add neighbor  as0 <-> as1 <-> as2

#echo "neighbor as0 as1"
#curl  -v -X POST http://152.3.136.36:7777/postNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\"] }"
#"message": "[u'_AXUnQ65WgSKbNyct8DRI82w2N4dPNAvBV4vTIbQVNk']"

#echo "neighbor as1 as0"
#curl  -v -X POST http://152.3.136.36:7777/postNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\"] }"

#"message": "[u'DsFwVHxiOaw62hUEKAhdHu1QT6OduJ68a8p6x2vnPqc']"

#echo "nb as2 as1"
#curl  -v -X POST http://152.3.136.36:7777/postNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\"] }"
#"message": "[u'2dNerjUUPvkbtbif2mbqFym5sfH5ZEpNBZe7sa2r0rA']"

#echo "nb as1 as2"
#curl  -v -X POST http://152.3.136.36:7777/postNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\"] }"
#"message": "[u'DsFwVHxiOaw62hUEKAhdHu1QT6OduJ68a8p6x2vnPqc']"
#echo "nb as3 as2"
#curl  -v -X POST http://152.3.136.36:7777/postNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\", \"otherValues\": [\"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\"] }"
#echo "nb as2 as3"
#curl  -v -X POST http://152.3.136.36:7777/postNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [\"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\"] }"
#eco "nb as3 as4"
#curl  -v -X POST http://152.3.136.36:7777/postNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\", \"otherValues\": [\"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\"] }"
#echo "nb as4 as3"
#curl  -v -X POST http://152.3.136.36:7777/postNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\", \"otherValues\": [\"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\"] }"
#echo "as4 as5"
#curl  -v -X POST http://152.3.136.36:7777/postNeighbor -H "Content-Type: application/json" -d "{ \"principal\": \"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\", \"otherValues\": [\"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\"] }"


#carryTraffic:
#echo "carry traffic as0 as1 as2"
#curl  -v -X POST http://152.3.136.36:7777/postCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\",\"ipv4\\\"152.3.1.1/16\\\"\"] }"

#echo "carry traffic as0 as1 as3"
#curl  -v -X POST http://152.3.136.36:7777/postCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\",\"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\"] }"
#
#echo "carry traffic as0 as1 as4"
#curl  -v -X POST http://152.3.136.36:7777/postCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\",\"ipv4\\\"166.111.1.1/16\\\"\"] }"
#
#echo "carry traffic as0 as1 as5"
#curl  -v -X POST http://152.3.136.36:7777/postCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\",\"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\"] }"
#
#echo "carry traffic as2 as1 as0"
#curl  -v -X POST http://152.3.136.36:7777/postCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\",\"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\"] }"
#"message": "[u'UmEJ3zNIdbg8zaySF93ehMBR4m6SrjT8ujTeiJqxGI8']"

#echo "carry traffic as1 as2 as5"
#curl  -v -X POST http://152.3.136.36:7777/postCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\",\"ipv4\\\"166.111.1.1/16\\\"\"] }"
##
#echo "carry traffic as2 as3 as5"
#curl  -v -X POST http://152.3.136.36:7777/postCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\", \"otherValues\": [\"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\",\"ipv4\\\"166.111.1.1/16\\\"\"] }"
##
#echo "carry traffic as3 as4 as5"
#curl  -v -X POST http://152.3.136.36:7777/postCarryTraffic -H "Content-Type: application/json" -d "{ \"principal\": \"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\", \"otherValues\": [\"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\",\"ipv4\\\"166.111.1.1/16\\\"\"] }"

#acceptRoute:
echo "acceptRoute as0 as 1 as2"
curl  -v -X POST http://152.3.136.36:7777/postAcceptRoute -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\",\"ipv4\\\"152.3.1.1/24\\\"\"] }"
curl  -v -X POST http://152.3.136.36:7777/postAcceptRoute -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\",\"ipv4\\\"152.3.2.1/24\\\"\"] }"
echo "acceptRoute as0 as 1 as5"
curl  -v -X POST http://152.3.136.36:7777/postAcceptRoute -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\",\"ipv4\\\"166.111.1.1/16\\\"\"] }"
echo "acceptRoute as 1 as 2 as5"
curl  -v -X POST http://152.3.136.36:7777/postAcceptRoute -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\",\"ipv4\\\"166.111.1.1/16\\\"\"] }"
curl  -v -X POST http://152.3.136.36:7777/postAcceptRoute -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\",\"ipv4\\\"152.3.1.1/24\\\"\"] }"
#
echo "carry traffic as2 as3 as5"
curl  -v -X POST http://152.3.136.36:7777/postAcceptRoute -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [\"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\",\"ipv4\\\"166.111.1.1/16\\\"\"] }"
#
echo "carry traffic as3 as3 as5"
#curl  -v -X POST http://152.3.136.36:7777/postAcceptRoute -H "Content-Type: application/json" -d "{ \"principal\": \"t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c\", \"otherValues\": [\"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\",\"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\"] }"
#
echo "rpki ip allocation"
curl  -v -X POST http://152.3.136.36:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\", \"otherValues\": [\"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\",\"ipv4\\\"152.3.1.1/24\\\"\"] }"
curl  -v -X POST http://152.3.136.36:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\", \"otherValues\": [\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\",\"ipv4\\\"152.3.2.1/24\\\"\"] }"
curl  -v -X POST http://152.3.136.36:7777/postIPAllocate -H "Content-Type: application/json" -d "{ \"principal\": \"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\", \"otherValues\": [\"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\",\"ipv4\\\"166.111.1.1/16\\\"\"] }"
#
echo "advertise route\n"
echo "as1 to as0: as1"
curl  -v -X POST http://152.3.136.36:7777/postAdvertise -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"ipv4\\\"152.3.2.1/24\\\"\",\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\",\"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\"] }"
echo "as1 to as0: as1/as2"
curl  -v -X POST http://152.3.136.36:7777/postAdvertise -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"ipv4\\\"152.3.1.1/24\\\"\",\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8/GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\",\"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\"] }"
echo "as2 to as1:as2"
curl  -v -X POST http://152.3.136.36:7777/postAdvertise -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [\"ipv4\\\"152.3.1.1/24\\\"\",\"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\",\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\"] }"

