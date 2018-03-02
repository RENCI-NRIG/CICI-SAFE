#!/bin/bash
echo "If there is existing slices, delete them"
./scripts/createslice.sh -c config/cnert-fl.conf -d
./scripts/createslice.sh -c client-config/c1-ufl.conf -d
./scripts/createslice.sh -c client-config/c3-ufl.conf -d
./scripts/createslice.sh -c client-config/c2-unf.conf -d
./scripts/createslice.sh -c client-config/c4-unf.conf -d

echo "creating slices"
./scripts/createslice.sh -c config/cnert-fl.conf
./scripts/createclientslice.sh -c client-config/c1-ufl.conf
./scripts/createclientslice.sh -c client-config/c3-ufl.conf
./scripts/createclientslice.sh -c client-config/c2-unf.conf
./scripts/createclientslice.sh -c client-config/c4-unf.conf

