---------------SAFE-SDX-----------------------------
To run the demo for SDX, you need to run a safe server first, and then run the ahab controller to start the sdx service

[1] run safe-server
  Deploy a riak server [you can use our riakserver directly 152.3.145.36:8098], or you can deploy a Riak server in Docker container
  $sudo docker pull yaoyj11/riakimg
  $sudo docker run -i -t  -d -p 2122:2122 -p 8098:8098 -p 8087:8087 -h riakserver --name riakserver yaoyj11/riakimg
  Start riak service
  $sudo docker exec -it riak sudo riak start
  $sudo docker exec -it riak sudo riak-admin bucket-type activate  safesets
  $sudo docker exec -it riak sudo riak-admin bucket-type update safesets '{"props":{"allow_mult":false}}'

[2] SDX service , SAFE_SDX directory

  1. build
  $./build.sh

  2. Run rmiresgistry in target/classes
  $cd target/classes
  $rmiregistry

  3. Create two customer slice and a service slice
    Launch a sdx slice: ./ahab.sh SliceName server true SDNControllerAddr sshkeypath [riakip]
    $./ahab.sh server server true 152.3.136.36 "~/.ssh/id_rsa" 152.3.145.36
    Launch multiple customer slices: ./ahab.sh Slicename client true [riakip]
    $./ahab.sh alice client true 192.168.10.1/24 152.3.145.36
    $./ahab.sh bob client true 192.168.20.1/24 152.3.146.36

  4. run slice controller (ahab) for sdx slice :./sdxserver.sh server 152.3.136.36 "~/.ssh/id_rsa"  server_keyhash PrivateIPPrefix
    $./sdxserver.sh server 152.3.136.36 "~/.ssh/id_rsa" bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E 192.168.30.1/20

  5. run slice controller(ahab) for sdx client:./sdxclient.sh alice client false  "~/.ssh/id_rsa" customerkeyhash
    $./sdxclient.sh alice client false "~/.ssh/id_rsa" weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24
    $./sdxclient.sh bob client false "~/.ssh/id_rsa" iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8

  6. Make SAFE statements to state the stitching and traffic policies, allocation of IP prefixes and stitching requests. (More details to be added)

  7. alice stitch CNode0 to sdx/c0
    $>stitch alice sdx CNode0 c0
    bob stitch CNode0 to sdx/c3
    $>stitch bob sdx CNode0 c3

  8. route
    $>route 192.168.10.1/24 192.168.33.2 server c0
    $>route 192.168.20.1/24 192.168.34.2 server c3

  9. setup routing in client side



  -----------------For Stitching and Routing Application (Out-Dated)----------------
   Each time a slice stitch to another one. It will store the neighboring information. such as edge router, gateway, neighbor's principal id;

   1. Launch multiple AS slices: ./ahab.sh SliceName server true SDNControllerAddr sshkeypath
  ./ahab.sh AS1 server true 152.3.136.36 "~/.ssh/id_rsa"
  ./ahab.sh AS2 server true 152.3.136.36 "~/.ssh/id_rsa"
  2. Launch multiple customer slices: ./ahab.sh Slicename client true
  ./ahab.sh Client1 client true
  ./ahab.sh Client2 client true

  3. Launch SDNController
  cd plexus/plexus
  ryu-manager app.py

  4. Run Ahab controller for AS slices
  (1) for AS: 
  ./routing.sh AS1 152.3.136.36 "~/.ssh/id_rsa" 152.3.136.36:7777  bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E 192.168.16.1/20
  ./routing.sh AS2 152.3.136.36 "~/.ssh/id_rsa" 152.3.136.36:7777  weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24 192.168.32.1/20

  Principal IDs available:
  AS1: key_p3: bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E
  iAS2: key_p2: weQ8OFpXWhIB1AMzKX2SDJcxT738VdHCcl7mFlvOD24
  Client1:key_p1: iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8
  Client2:key_p4: V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8
  key_p5: UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA

  5. Run ahab controller for AS client
  ./routingclient.sh Client1 client false  "~/.ssh/id_rsa" iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8  "http://152.3.136.36:7777"
  ./routingclient.sh Client2 client false  "~/.ssh/id_rsa" V1F2853Nq8V304Yb_GInYaWTgVqmBsQwC0tXWuNmmf8  "http://152.3.136.36:7777"

  6. stitch
  1)In AS1 controller:
  stitch AS1 AS2 c3 c3
  2) In Client1 controller:
  stitch Client1 AS1 CNode0 c0
  3) In Client2 controller:
  stitch Client2 AS2 CNode0 c0

  7. advertise route
  1) in Client1 
  route 192.168.19.2/24 AS1
  2) in Client2 route 192.168.36.2/24 AS2
  Note: The AS will readvertise the routes to its neighbors. When the client receive the route advertisement it will configure the routing with quagga/zebra. Note that in zebra.conf we need to specify the gateway address, not only interface name.
  problem: The OVS configuration on the stitch port is not successful. It sends the packet to the controller instead of routing it.
  How set static routing work in Ryu. If first send an arp request to the dst gateway and when it receives the reply, it set up the routing flows accordingly.

