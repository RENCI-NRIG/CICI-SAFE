#!/bin/sh
#key_p3: bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E
#key_p2: weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24
#key_p1: iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8
#key_p4: V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8
#key_p5: UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA
#curl  http://152.3.136.36:7777/verifyStitch -H "Content-Type: application/json" -d "{ \"principal\": \"bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E\", \"otherValues\": [\"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\",\"USzSn2rk4lu_epuwoAMwigcrT_40OOsoSl30ttXtY1I\"] }"
curl  http://152.3.136.36:7777/verifyStitch -H "Content-Type: application/json" -d "{ \"principal\": \"bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E\", \"otherValues\": [\"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\",\"client\",\"4db2b812-39ac-4333-9417-07adef946e68\",\"server\",\"c0\"] }"
curl  http://152.3.136.36:7777/connectivity -H "Content-Type: application/json" -d "{ \"principal\": \"bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E\", \"otherValues\": [\"weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24\",\"iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8\",\"192.168.10.1/24\",\"192.168.20.1/24\"] }"
#curl  http://152.3.136.36:7777/queryPath -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\"] }"
#curl  http://152.3.136.36:7777/verifyRoute -H "Content-Type: application/json" -d "{ \"principal\": \"iCqAs3PcsfGWjF3Ywcr47-0Jfd15Z7EQ3v6sSVza8KY\", \"otherValues\": [\"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\",\"eZz9xWGKCwBdf8VjRtuSXYfaO1jLgjwOfrX8N3s14f8/GA6A1fkLhsWUB7SKaPSCw8DO_ozLVM-Q8l0NdsqwRiA/t8UXUgNmiZY8jloIIvCxi9jSjzXUaokqdwtzvyoy95c/eQ-b5wEKqzjI1PSCuqZY6EVQdMWyGAZob2JzkW5pLsg/XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\"] }"
