#!/bin/bash
#as0 iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY
#as1 eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8
#as2 GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA
#as3 t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c
#as4 eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg
#rpkiroot XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU

echo "updatePathSet: AS0: as1"
curl  -v -X POST http://152.3.136.36:7777/postPathToken -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"7_-9mpVcaNNMypyI7P_EVmG7CvsDDAMhGgwQavNFk-U\",\"[eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8]\"] }"
echo "updatePathSet: as0 : as1/as2"
curl  -v -X POST http://152.3.136.36:7777/postPathToken -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"uigBz2av8LsdxozoL5QeJHG-3IG6s27IlL7PIaXwd5E\",\"[eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8,GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA]\"] }"
echo "updatePathSet: as1: as2"
curl  -v -X POST http://152.3.136.36:7777/postPathToken -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"tZ_OsjQwsdK_td2Kh2KrJ2UQJUmRgxPCn2E6Z3qoHos\",\"[GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA]\"] }"

echo "post ip token"
curl  -v -X POST http://152.3.136.36:7777/postDlgToken -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [\"VSyb08QIhVywIBwoZijTPIVK_4b68G-_xQS40DB6O7I\"] }"
curl  -v -X POST http://152.3.136.36:7777/postDlgToken -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"VSyb08QIhVywIBwoZijTPIVK_4b68G-_xQS40DB6O7I\"] }"
curl  -v -X POST http://152.3.136.36:7777/postDlgToken -H "Content-Type: application/json" -d "{ \"principal\": \"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\", \"otherValues\": [\"VSyb08QIhVywIBwoZijTPIVK_4b68G-_xQS40DB6O7I\"] }"
curl  -v -X POST http://152.3.136.36:7777/postDlgToken -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [\"KQjsO2g9UvQesJTbKzVCY89DuU5o5hRlYXicAuse4Kc\"] }"
