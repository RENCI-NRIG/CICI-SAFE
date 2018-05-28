#!/bin/bash
SDX=sdx-uh-tamu-yaoyj11
echo "stitch clients"
./scripts/sdx_exogeni_client.sh -c client-config/c1.conf -e "stitch CNode0 ${SDX} e0"
./scripts/sdx_exogeni_client.sh -c client-config/c2.conf -e "stitch CNode0 ${SDX} e1"
./scripts/sdx_exogeni_client.sh -c client-config/c3.conf -e "stitch CNode0 ${SDX} e0"
./scripts/sdx_exogeni_client.sh -c client-config/c4.conf -e "stitch CNode0 ${SDX} e1"

echo "advertise ip prefixes, users can parse the gateway ip address from the output of the previous commands"

./scripts/sdx_exogeni_client.sh -c client-config/c1.conf -e  "route 192.168.10.1/24 192.168.132.2"
./scripts/sdx_exogeni_client.sh -c client-config/c2.conf -e "route 192.168.20.1/24 192.168.133.2"
./scripts/sdx_exogeni_client.sh -c client-config/c3.conf -e  "route 192.168.30.1/24 192.168.134.2"
./scripts/sdx_exogeni_client.sh -c client-config/c4.conf -e "route 192.168.40.1/24 192.168.135.2"

"echo request for connections"

./scripts/sdx_exogeni_client.sh -c client-config/c1.conf -e  "link 192.168.10.1/24 192.168.20.1/24"
./scripts/sdx_exogeni_client.sh -c client-config/c3.conf -e  "link 192.168.30.1/24 192.168.40.1/24"
