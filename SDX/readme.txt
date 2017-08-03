---------------SAFE-SDX-----------------------------
To run the demo for SDX, you need to run a safe server first, and then run the ahab controller to start the sdx service

[1] run riak-server
  Deploy a riak server [you can use our riakserver directly 152.3.145.36:8098], or you can deploy a Riak server in Docker container
  $sudo docker pull yaoyj11/riakimg
  $sudo docker run -i -t  -d -p 2122:2122 -p 8098:8098 -p 8087:8087 -h riakserver --name riakserver yaoyj11/riakimg
  Start riak service
  $sudo docker exec -it riakserver sudo riak start
  $sudo docker exec -it riakserver sudo riak-admin bucket-type activate  safesets
  $sudo docker exec -it riakserver sudo riak-admin bucket-type update safesets '{"props":{"allow_mult":false}}'

[2] SDX service , SAFE_SDX directory

  1. build
  $./build.sh

  2. Run rmiresgistry in target/classes
  $cd target/classes
  $rmiregistry

  3. Start Plexus SDN controller
    cd plexus/plexus
    ryu-manager app.py

  4. Create two customer slice and a service slice: ./ahab.sh ConfigFile
    Launch a sdx slice: (1) set the ip address of riak server in src/main/resources/sdx.conf; (2) ./ahab.sh sdx
    Launch multiple customer slices: ./ahab.sh Slicename client true [riakip]
    $./ahab.sh alice
    $./ahab.sh bob

  5. run slice controller (ahab) for sdx slice :./sdxserver.sh ConfigFile
    $./sdxserver.sh sdx

  6. run slice controller(ahab) for sdx client:./sdxclient.sh ConfigFile
    $./sdxclient.sh alice
    $./sdxclient.sh bob

  7. post SAFE identity sets, make SAFE statements to state the stitching and traffic policies, allocation of IP prefixes and stitching requests.
    $ cd safe/super-safe/safe-apps/safe-network/exo-geni
    Edit the SAFESERVER ip address to your safe server IP address in idset.sh, post.sh and updatess.sh, and run following scripts to make posts to safesets. Messages with a token in each message are expected.
    $./idset.sh
    $./post.sh
    $./updatess.sh PROJECTENDORSEMENT_TOKEN
    Here PROJECTENDORSEMENT_TOKEN is the token for project member endorsement sets, this is the token in last 4th, 5th and 6th messages of the running result post.sh
    $./updatess.sh IPDELEGATION_TOKEN
    Here IPDELEGATION_TOKEN is the token for ip deletation sets, this is the token in last, 2nd and 3rd messages of the running result post.sh
    For example, if the messages of of post.sh are as follows:
        > POST /postIPAllocate HTTP/1.1
        > Host: 152.54.14.38:7777
        > User-Agent: curl/7.47.0
        > Accept: */*
        > Content-Type: application/json
        > Content-Length: 144
        >
        * upload completely sent off: 144 out of 144 bytes
        < HTTP/1.1 200 OK
        < Server: spray-can/1.3.3
        < Date: Mon, 10 Jul 2017 23:06:47 GMT
        < Content-Type: application/json; charset=UTF-8
        < Content-Length: 66
        <
        {
            "message": "['mjpKqF0WsgUKlhCnIYpa4_CrgOo4gIIlGqLjepxJJ5E']"
            * Connection #0 to host 152.54.14.38 left intact
        }pa endorse alice
        Note: Unnecessary use of -X or --request, POST is already inferred.
        *   Trying 152.54.14.38...
        * Connected to 152.54.14.38 (152.54.14.38) port 7777 (#0)
        > POST /postEndorsePM HTTP/1.1
        > Host: 152.54.14.38:7777
        > User-Agent: curl/7.47.0
        > Accept: */*
        > Content-Type: application/json
        > Content-Length: 126
        >
        * upload completely sent off: 126 out of 126 bytes
        < HTTP/1.1 200 OK
        < Server: spray-can/1.3.3
        < Date: Mon, 10 Jul 2017 23:06:47 GMT
        < Content-Type: application/json; charset=UTF-8
        < Content-Length: 66
        <
        {
            "message": "['jkgOKYbQhgQH-uTCkUVqh45Zge8SR_SkOXkvx6chvyA']"
            * Connection #0 to host 152.54.14.38 left intact
        }pa endorse bob
        Note: Unnecessary use of -X or --request, POST is already inferred.
        *   Trying 152.54.14.38...
        * Connected to 152.54.14.38 (152.54.14.38) port 7777 (#0)
        > POST /postEndorsePM HTTP/1.1
        > Host: 152.54.14.38:7777
        > User-Agent: curl/7.47.0
        > Accept: */*
        > Content-Type: application/json
        > Content-Length: 126
        >
        * upload completely sent off: 126 out of 126 bytes
        < HTTP/1.1 200 OK
        < Server: spray-can/1.3.3
        < Date: Mon, 10 Jul 2017 23:06:47 GMT
        < Content-Type: application/json; charset=UTF-8
        < Content-Length: 66
        <
        {
            "message": "['jkgOKYbQhgQH-uTCkUVqh45Zge8SR_SkOXkvx6chvyA']"
            * Connection #0 to host 152.54.14.38 left intact
        }Note: Unnecessary use of -X or --request, POST is already inferred.
        *   Trying 152.54.14.38...
        * Connected to 152.54.14.38 (152.54.14.38) port 7777 (#0)
        > POST /postEndorsePM HTTP/1.1
        > Host: 152.54.14.38:7777
        > User-Agent: curl/7.47.0
        > Accept: */*
        > Content-Type: application/json
        > Content-Length: 126
        >
        * upload completely sent off: 126 out of 126 bytes
        < HTTP/1.1 200 OK
        < Server: spray-can/1.3.3
        < Date: Mon, 10 Jul 2017 23:06:48 GMT
        < Content-Type: application/json; charset=UTF-8
        < Content-Length: 66
        <
        {
            "message": "['jkgOKYbQhgQH-uTCkUVqh45Zge8SR_SkOXkvx6chvyA']"
            * Connection #0 to host 152.54.14.38 left intact
    You should run ./updatess.sh mjpKqF0WsgUKlhCnIYpa4_CrgOo4gIIlGqLjepxJJ5E and ./updatess.sh jkgOKYbQhgQH-uTCkUVqh45Zge8SR_SkOXkvx6chvyA

  8. alice stitch CNode0 to sdx/c0, in alice's controller, run:
    $>stitch alice sdx CNode0 c0
    bob stitch CNode0 to sdx/c3, in bob's controller run:
    $>stitch bob sdx CNode0 c3

  9. route
    alice tells sdx controller its address space
    $>route 192.168.10.1/24 192.168.33.2 server c0
    bob tells sdx controller its address space
    $>route 192.168.20.1/24 192.168.34.2 server c3

  10. setup routing in client side
    An example command of adding an entry to the routing table is as follows, this only supports dest IP address with /32 netmask
    Another way to do this is using Quagga with zebra enabled, and add routing entries in zebra.conf, dest ip with any netmask is supported
    In the demo, to enable communication between CNode1 in alice and CNode1 in bob, the commands are:
    CNode1-alice$ ip route add 192.168.20.2/32 via 192.168.10.1
    CNode0-alice$ ip route add 192.168.20.2/32 via 192.168.34.1
    CNode1-bob$  ip route add 192.168.10.2/32 via 192.168.20.1
    CNode0-bob$ ip route add 192.168.10.2/32 via 192.168.33.1
    


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

