#!/bin/bash
./scripts/sdxclient.sh -c config/c1.conf -n -e "stitch CNode0 sdx"
#30.2
./scripts/sdxclient.sh -c config/c2-ufl.conf -n -e "stitch CNode0 sdx"
#31.2
./scripts/sdxclient.sh -c config/c3-chi.conf -n -e "stitch CNode0 sdx"
#32.2
./scripts/sdxclient.sh -c config/c2-ufl.conf -n -e "link UFL SL"
./scripts/sdxclient.sh -c config/c2-ufl.conf -n -e "route 192.168.20.1/24 192.168.31.2"

./scripts/sdxclient.sh -c config/c3-chi.conf -n -e "route 192.168.40.1/24 192.168.32.2"
./scripts/sdxclient.sh -c config/c1.conf -n -e "link UFL TAMU"

./scripts/sdxclient.sh -c config/c1.conf -n -e "route 192.168.10.1/24 192.168.30.2"
#30.2
