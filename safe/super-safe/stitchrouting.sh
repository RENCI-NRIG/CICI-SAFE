#!/bin/bash
sbt  "project safe-server" "run -f /home/yaoyj11/project/exo-geni/safe/super-safe/safe-apps/safe-network/exo-geni/stitch-routing.slang  -r safeService  -kd   src/main/resources/key" 
