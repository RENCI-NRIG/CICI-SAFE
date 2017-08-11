#!/bin/sh
#as0 iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY
#as1 eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8
#as2 GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA
#as3 t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c
#as4 eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg
#as5 XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU
curl  http://152.3.136.36:7777/queryPath -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\"] }"
curl  http://152.3.136.36:7777/queryPath -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\"] }"
curl  http://152.3.136.36:7777/verifyRoute -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\",\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8/GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA/t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c/eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg/XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\"] }"
