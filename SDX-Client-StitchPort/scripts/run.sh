#!/bin/bash
#stitch stitchporturl vlan sdxslice sdxnode gateway_in_chameleon ip_for_new_interface_in_exogeni
#stitch http://geni-orca.renci.org/owl/ion.rdf#AL2S/Chameleon/Cisco/6509/GigabitEthernet/1/1 3299 sdx c1 10.32.99.217 10.32.99.101/24
./SDX_StitchPort_Client/target/appassembler/bin/SafeStitchPortClient $1 $2 $3 $4
