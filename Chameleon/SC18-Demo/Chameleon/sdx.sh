#!/bin/bash

openstack stack create --template CICI-SAFE-v0.1.yaml \
--parameter "key_name=pruth-chameleon" \
--parameter "reservation_id=0140dbe9-76a5-43f6-8ff6-64a53e483227" \
--parameter "network_name=1a03cf65-8fd6-4fce-94fd-bbaabe68a8e1" \
pruth-sdx
