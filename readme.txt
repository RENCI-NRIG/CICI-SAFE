---------------SAFE-SDX-----------------------------
To run the SDX demo, first we creat a SDX slice and two customer slices on exogeni.

[1] Run riak-server
    Launch a riak server with code and instructions in SAFE-Riak-Server

[2] Create a SDX slice
  a) $cd SDX-Simple
  b) Edit configuration file for sdx slice "config/sdx.conf"
  c) build
     $./scripts/build.sh
  d) create SDX slice
     $./scritps/createslice.sh -c config/sdx.conf

[3] Create two customer slices
  a) $cd SDX-Client-ExoGENI
  b) Edit configuration files for alice and bob, "config/alice.conf" and "config/bob.conf" 
  c) build
     $./scripts/build.sh
  d) create alice slice and bob slice
     $./scripts/createslice.sh -c config/alice.conf
     $./scripts/createslice.sh -c config/bob.conf

Then we can run ahab controllers for sdx, alice and bob. 
 [4] Run sdx server controller, configure the address and port number that sdx server will listen on ("config.serverurl").
     $./scripts/sdxserver.sh -c config/sdx.conf

 [5] Configure the address of SDX server controller ("config.sdxserver") in configuration files and run controller for alice and bob, 
     $./scripts/sdxclient.sh -c config/alice.conf
     $./scripts/sdxclient.sh -c config/bob.conf

 [6]. post SAFE identity sets, make SAFE statements to state the stitching and traffic policies, allocation of IP prefixes and stitching requests.
    $ cd safe-scripts 
    Edit the SAFESERVER ip address to your safe server IP address in idset.sh, post.sh and updatess.sh, and run following scripts to make posts to safesets. Messages with a token in each message are expected.
    $./idset.sh
    $./post.sh
    $./updatess.sh 

  7. alice stitch CNode0 to sdx/c0, in alice's controller, run:
    $>stitch alice sdx CNode0 c0
    bob stitch CNode0 to sdx/c3, in bob's controller run:
    $>stitch bob sdx CNode0 c3

  8. route
    alice tells sdx controller its address space
    $>route 192.168.10.1/24 192.168.33.2 sdx c0
    bob tells sdx controller its address space
    $>route 192.168.20.1/24 192.168.34.2 sdx c3

  9. setup routing in client side
    An example command of adding an entry to the routing table is as follows, this only supports dest IP address with /32 netmask
    Another way to do this is using Quagga with zebra enabled, and add routing entries in zebra.conf, dest ip with any netmask is supported
    In the demo, to enable communication between CNode1 in alice and CNode1 in bob, the commands are:
    CNode1-alice$ ip route add 192.168.20.2/32 via 192.168.10.1
    CNode0-alice$ ip route add 192.168.20.2/32 via 192.168.33.1
    CNode1-bob$  ip route add 192.168.10.2/32 via 192.168.20.1
    CNode0-bob$ ip route add 192.168.10.2/32 via 192.168.34.1

  10. Delete a slice
    we can delete a slice with command: ./scripts/createslice.sh -c configFile -d
    For exmaple: ./scripts/createslice.sh -c config/alice.conf -d
    
