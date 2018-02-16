#!/bin/bash

# Run SAFE server

cd ~/SAFE/uber-safe

 ~/sbt/bin/sbt "project safe-server" "run -f /root/SAFE/uber-safe/safe-apps/cloud-attestation/latte.slang  -r safeService  -kd   src/main/resources/multi-principal-keys/"

/bin/bash
