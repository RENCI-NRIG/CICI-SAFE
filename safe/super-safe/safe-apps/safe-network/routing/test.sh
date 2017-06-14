curl  -v -X POST http://152.3.136.36:7777/posttestIP -H "Content-Type: application/json" -d "{ \"principal\": \"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\", \"otherValues\": [] }"

curl  http://152.3.136.36:7777/ipInRange -H "Content-Type: application/json" -d "{ \"principal\": \"XLajumPCpZWScMJUoxrApXMCxRllpAVnFIwzv7CFpsU\", \"otherValues\": [\"ipv4\\\"152.3.2.1/24\\\"\",\"ipv4\\\"152.3.2.1/16\\\"\"] }"

