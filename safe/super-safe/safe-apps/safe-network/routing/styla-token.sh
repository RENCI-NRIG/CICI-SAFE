#!/bin/bash
#as0 iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY
#as1 eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8
#as2 GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA
#as3 t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c
#as4 eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg
#rpkiroot XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU
#curl http://152.3.145.15:8098/types/safesets/buckets/safe/keys/HZbAMrzBHwU9mWOm_7na-V7tOIY2NOH95kvGwqBlKVo/?vtag=3AWzKUjhNYy65bEUbLbsIi

echo "updatePathSet: AS0: as1"
curl  -v -X POST http://152.3.136.36:7777/postPathToken -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"k1XTwD8w-oGkoRsW4QH-w3oAACkLbFt22HMNspbTxXc\",\"[eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8]\"] }"
echo "updatePathSet: as0 : as1/as2"
curl  -v -X POST http://152.3.136.36:7777/postPathToken -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"igl0VSp-m8iRKD7AC9sJWeIDPcVM-vmMZ2A2ctw3S4k\",\"[eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8,GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA]\"] }"
echo "updatePathSet: as1: as2"
curl  -v -X POST http://152.3.136.36:7777/postPathToken -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"OrNpGUFpP0FSouWYxKJUu6cU6iL2VBCsyTy85DJU37M\",\"[GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA]\"] }"

echo "post ip token"
echo "---------IP allocation 1"
curl  -v -X POST http://152.3.136.36:7777/postDlgToken -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [\"IHPsAJpz3pGzwsaLC0c-14V2e2XiADtWxp714WVjTxc\"] }"
echo "---------IP allocation 2 "
curl  -v -X POST http://152.3.136.36:7777/postDlgToken -H "Content-Type: application/json" -d "{ \"principal\": \"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8\", \"otherValues\": [\"IHPsAJpz3pGzwsaLC0c-14V2e2XiADtWxp714WVjTxc\"] }"
echo "---------IP allocation 3"
curl  -v -X POST http://152.3.136.36:7777/postDlgToken -H "Content-Type: application/json" -d "{ \"principal\": \"eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg\", \"otherValues\": [\"IHPsAJpz3pGzwsaLC0c-14V2e2XiADtWxp714WVjTxc\"] }"
echo "---------IP allocation 4"
curl  -v -X POST http://152.3.136.36:7777/postDlgToken -H "Content-Type: application/json" -d "{ \"principal\": \"GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA\", \"otherValues\": [\"KMDw2sNTVXCNdEEecsTo7yOsBhA6U3uMiaOyH2H9SUw\"] }"
