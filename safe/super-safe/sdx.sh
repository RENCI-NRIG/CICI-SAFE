#!/bin/bash
sbt  "project safe-server" "run -f /home/yaoyj11/project/integration/safe-apps/safe-network/stitch/stitch.slang  -r safeService  -kd   src/main/resources/key" 
