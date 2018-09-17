#!/bin/bash

openstack stack create --template CICI-SAFE-sdx.yaml \
--parameter "key_name=pruth-chameleon" \
--parameter "reservation_id=4d994fc1-cc7e-490e-bc8c-df8116edb530" \
--parameter "network_name=a772a899-ff3d-420b-8b31-1c485092481a" \
pruth-sdx
