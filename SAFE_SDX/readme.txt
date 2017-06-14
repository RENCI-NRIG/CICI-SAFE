Create two customer slice and a service slice

1. build
./build.sh

2. Run rmiresgistry in target/classes
cd target/classes
rmiregistry

3. run network manager
./slicenetwork.sh

4. Run service slice
./run.sh SliceName server true/false controllerIP
Example: ./run.sh server server true 152.3.136.36
#true if create a new slice, false if load an existing slice

5. Run client slice
./run.sh ClientName client true/false 
#true if create a new slice, false if loading an existing slice
example: ./run.sh client client true
[enter commands in client command line]
(1) PRESS "ENTER" to continue
(2) enter stitch commands
  Example: stitch client server CNode0 stitch00
  Example: stitch client server CNode1 stitch30
enter "y"+ENTER to confirm. otherwise abort
(3) enter advertise route commands
  route PREFIX_TO_ADVERTISE TargeRouter GatewayIP_in_CustomerSlice
  Example: route 192.168.133.2/24 c0 192.168.133.2
  Example: route 192.168.133.2/24 c3 192.168.133.2
(4) set up routing in customer slice 
  a) use ip route add command, works with /32 address only
  Example: ip route add 192.168.133.2/32 via 192.168.134.1
  Example: ip route add 192.168.134.2/32 via 192.168.133.1

6. Delete a slice
./ahab.sh SliceName delete

NOTE: 1. You need to change the absolute address of the scripts according to your directory path
2. If you run step 4 with false option, you may need to reconfigure the ip address of customer node,according to the gateway address in the output.  make sure to turn off neuca in customer node [/etc/init.d/neuca stop], 


4/3
3. ./run.sh server 152.3.136.36 "~/.ssh/id_rsa" server_keyhash safe-server server_keyhash PrivateIPPrefix
./run.sh server 152.3.136.36 "~/.ssh/id_rsa" server_keyhash http://152.3.136.36:7777  bphJZn3RJBnNqoCZk6k9SBD8mwSb054PXbwV7HpE80E 192.168.16.1/20
note: 
  (1) Dynamically create a new interface, i.e., a new Vlan for when stitching, 
  assign an ip address to the interface, 
  hard code the ip address in the name of the Vlan (which is stored by exogeni SM, consistent across server failure)
  (2) When add a new interface to ovs bridge, the SDN controller won't know it. The port number and mac address of
  the port is told to SDN controller when it first connects to the controller. What I do is, each time I add a new interface to the ovs bridge
  I disconnect the ovs bridge from the controller and then reconnects to it. And then replay the configurations on that ovs bridge.

2. ./ahab.sh client client true  "~/.ssh/id_rsa" customerkeyhash safeserver


-----------------For Stitching and Routing Application----------------
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
