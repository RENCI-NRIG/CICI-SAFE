#!/bin/bash
echo "If there is existing slices, delete them"
./scripts/createslice.sh -c config/sdx.conf -d
./scripts/createslice.sh -c client-config/c1.conf -d
./scripts/createslice.sh -c client-config/c3.conf -d
./scripts/createslice.sh -c client-config/c2.conf -d
./scripts/createslice.sh -c client-config/c4.conf -d

echo "creating slices"
./scripts/createslice.sh -c config/sdx.conf
./scripts/createclientslice.sh -c client-config/c1.conf
./scripts/createclientslice.sh -c client-config/c3.conf
./scripts/createclientslice.sh -c client-config/c2.conf
./scripts/createclientslice.sh -c client-config/c4.conf

