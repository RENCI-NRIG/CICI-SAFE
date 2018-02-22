To build:
./scritps/build.sh

To create a client slice:
./scritps/createslice.sh -c config/c1.conf

To run controller:
./scritps/sdxclient.sh -c config/sdx.conf 
./scritps/sdxclient.sh -c config/sdx.conf -c "command to exec [stitch CNode0 sdx]"
./scritps/sdxclient.sh -c config/sdx.conf -c "command to exec [route 192.168.10.1/24 192.168.34.2]"

