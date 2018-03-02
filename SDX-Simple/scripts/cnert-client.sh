#!/bin/bash
echo "stitch clients"
./scripts/sdx_exogeni_client.sh -c client-config/c1-ufl.conf -e "stitch CNode0 sdx c0"
./scripts/sdx_exogeni_client.sh -c client-config/c2-unf.conf -e "stitch CNode0 sdx c1"
./scripts/sdx_exogeni_client.sh -c client-config/c3-ufl.conf -e "stitch CNode0 sdx c0"
./scripts/sdx_exogeni_client.sh -c client-config/c4-unf.conf -e "stitch CNode0 sdx c1"

echo "advertise ip prefixes, users can parse the gateway ip address from the output of the previous commands"

./scripts/sdx_exogeni_client.sh -c client-config/c1-ufl.conf -e  "route 192.168.10.1/24 192.168.130.2"
./scripts/sdx_exogeni_client.sh -c client-config/c2-unf.conf -e "route 192.168.20.1/24 192.168.131.2"
./scripts/sdx_exogeni_client.sh -c client-config/c3-ufl.conf -e  "route 192.168.30.1/24 192.168.132.2"
./scripts/sdx_exogeni_client.sh -c client-config/c4-unf.conf -e "route 192.168.40.1/24 192.168.133.2"

"echo request for connections"

./scripts/sdx_exogeni_client.sh -c client-config/c1-ufl.conf -e  "link 192.168.10.1/24 192.168.20.1/24"
./scripts/sdx_exogeni_client.sh -c client-config/c3-ufl.conf -e  "link 192.168.30.1/24 192.168.40.1/24"
