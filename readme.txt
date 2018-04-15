---------------Experiment Demo---------------------------
To run the exerimental demo.
0. Install ahab 1.7( https://github.com/RENCI-NRIG/ahab.git) manually. "cd ahab; mvn install"

Enter SAFE_HOME/SDX-Simple  (SAFE_HOME refers to the root directory of the project, same below)
1. build: ./scripts/build.sh

Enter SAFE_HOME
2. Modify fields in "configure" and run "./configure", this script will substitute fields in configuration files
   You should at least change the path to your exogeni pem file. And  you can choose what suffix you use for your slices, what sites you want to experiment on. 
   Note: If you are running on Mac, use configure_mac instead. The commands for sed on mac and linux are slightly different.

   About configuration files in SDX-Simple/config and SDX-Simple/client-config: 
        slicename       Name of the slice
        sshkey          the location of your sshkey, used for logging onto ExoGENI node
        exogenipem      Your ExoGENI pem file
        serverurl       The ip address and port number of vSDX slice controller
        scritpsdir      /Path/to/SDX/SDX-Simple/SAFE_SDX/src/main/resources/
        topodir         /Path/to/SDX/SDX-Simple/topo/
        Generally, you need to change the above configuration fields, and leave the rest alone.

        clientsites     the expected sites of clients where we need to launch some routers when we create the slice
        routernum       The number of routers to be launched when creating the slice
        controllersite  The site of plexus sdn controller
        sitelist        The list of sites that customer can request for stitching [Some of the sites may fail depending on the status]
        bw              The bandwidth of links between vSDX routers
        brobw           The bandwidth of links between routers and Bro node
        bro             whether deploy bro when creating the vsdx or not

   For demo with Both ExoGENI and Chameleon, [to be updated]

3. There is two version of Demo. One is java code in TestMain. In TestMain we run server controllers and multiple client controllers in the same program, and client controllers interact with server controllers via HTTP restful APIs.
   cd SAFE_HOME/SDX-Simple
   Run: ./scripts/test.sh  [Options]
           Options:
             -s/--slice     use existing slice
             -r/--reset     Clear SDX slice and undo previous operations

   Note: In the demo, the is a vSDX slice controller, and multiple client slice controllers. The client controllers interact with vSDX controller via HTTP Restful APIs. The test program mimics the work flow in real case, the ip addresses and slice names are specific to the test demo. If you change the IP prefix in the configuration files, you need to change them in the test code accordingly.

   The work flow in real case, (1) we start vSDX controller independently, (2) start multiple client controllers,(3) clients enter command-line commands for request for network stitching, and get the IP address of the gateway in SDX slice (for example 192.168.130.1/24), and set the IP address in customer node an IP address in the same subnet, say 192.168.130.2/24 (4) advertise IP prefix to vSDX controller (5) request for connections between two IP prefixes.
   
   Another version of the demo is implemented with bash scripts, it runs the controllers seperately [To be tested, the command for vsdx server might never return, as there is http server listening all the time. If so, you might split the scripts into two files and run clients code after the server code completes].
   cd SAFE_HOME/SDX-Simple
   ./scripts/cnert-slices.sh
   ./scripts/cnert-server.sh [Options]
           Options:
                -r//--reset  Clear SDX slice and undo previous operations
   ./scripts/cnert-client.sh [wait until vsdx server starts]

4. Bro intrusion detection
   SdxController will restart bro instances each time it restarts. After the mirroring flows have been set up, we can transfer evil files between customer nodes with ftp and Bro will detect it and cut the connection.
   Example command to transfer the file:
   [on c3]$ wget ftp://ftpuser:ftp@192.168.40.2/evil.txt
   After about 5 seconds, the connection between 192.168.30.2 and 192.168.40.2 will be cut

A few example scenerios and related commands:
Enter SAFE_HOME/SDX_Simple
Scenerio 1: Start from nothing
    option 1:
    ./scripts/test.sh
    option 2:
    ./scripts/cnert-slices.sh
    ./scripts/cnert-server.sh
    ./scripts/cnert-client.sh

Scenerio 2: After stitching client slices and deploying bro nodes, we want to remove the stitching and bro nodes and run the demo again.
    option 1:
    ./scripts/test.sh -r -s

    option 2:
    ./scripts/cnert-server.sh -r
    ./scripts/cnert-client.sh

Scenerio 3: Rerun the demo to set up connection again, while using previous stitching and bro nodes.
    ./scripts/cnert-server.sh
    ./scripts/sdx_exogeni_client.sh -c client-config/c1.conf -e  "route 192.168.10.1/24 192.168.130.2"
    ./scripts/sdx_exogeni_client.sh -c client-config/c2.conf -e "route 192.168.20.1/24 192.168.131.2"
    ./scripts/sdx_exogeni_client.sh -c client-config/c3.conf -e  "route 192.168.30.1/24 192.168.132.2"
    ./scripts/sdx_exogeni_client.sh -c client-config/c4.conf -e "route 192.168.40.1/24 192.168.133.2"

    ./scripts/sdx_exogeni_client.sh -c client-config/c1.conf -e  "link 192.168.10.1/24 192.168.20.1/24"
    ./scripts/sdx_exogeni_client.sh -c client-config/c3.conf -e  "link 192.168.30.1/24 192.168.40.1/24"



-------------- Bro Experiment --------------------
Two run the bro experiment, first we need to set up FTP service. The TestSlice code should have already set up FTP service when it creates the slice. 
1. create the test slice: [1] modify TestMain to call emulationSlice() in main(). [2] modify configuration files to specify two sites. Since we need large bandwdith between two sites. It's highly suggested to choose UH-TAMU, or any pair from (UFL, UNF, FIU). [3] run TestMain to create the emulation slice
2. Run BroMain for the experiment.
   ./scripts/bro.sh



---------------SAFE-SDX-----------------------------
To run the SDX demo, first we creat a SDX slice and two customer slices on exogeni.

  [1] Create a SDX slice
  a) $cd SDX-Simple
  b) Edit configuration file for sdx slice "config/sdx.conf"
  c) build
     $./scripts/build.sh
  d) create SDX slice
     $./scritps/createslice.sh -c config/sdx.conf

  [2] Create two customer slices
   a) $cd SDX-Simple
   b) Edit configuration files for alice and bob, "config/alice.conf" and "config/bob.conf" 
   c) build
      $./scripts/build.sh
   d) create alice slice and bob slice
      $./scripts/createclientslice.sh -c client-config/alice.conf
      $./scripts/createclientslice.sh -c client-config/bob.conf

      Then we can run slice controllers for sdx, alice and bob. 
  [3] Run sdx server controller, configure the address and port number that sdx server will listen on ("config.serverurl").
      $./scripts/sdxserver.sh -c config/sdx.conf

  [4] Configure the address of SDX server controller ("config.sdxserver") in configuration files and run controller for alice and bob, 
     $./scripts/sdxclient.sh -c client-config/alice.conf
     $./scripts/sdxclient.sh -c client-config/bob.conf

  [6]. alice stitch CNode0 to sdx/c0, in alice's controller, run:
    $>stitch CNode0 [SDX_SLICE_NAME, e.g., sdx] [optional, the name of router in vsdx slice if the client knows exactly which router it wants to stitch to, e.g., c0]
    bob stitch CNode0 to sdx/c3, in bob's controller run:
    $>stitch CNode [SDX_SLICE_NAME, e.g., sdx]
    
    OR the following commands are equivalent:
    ./scripts/sdxclient.sh -c client-config/alice.conf -e "stitch CNode0 [SDX_SLICE_NAME, e.g., sdx]"
    ./scripts/sdxclient.sh -c client-config/bob.conf -e "stitch CNode0 [SDX_SLICE_NAME, e.g., sdx]"

  [7]. advertise prefix
    alice tells sdx controller its address space
    $>route 192.168.10.1/24 192.168.33.2
    bob tells sdx controller its address space
    $>route 192.168.20.1/24 192.168.34.2
    
    OR the following commands are equivalent:
    ./scripts/sdxclient.sh -c client-config/alice.conf -e "route 192.168.10.1/24 192.168.33.2"
    ./scripts/sdxclient.sh -c client-config/bob.conf -e "route 192.168.20.1/24 192.168.34.2"

  [8] customer connection request
    $>link [IP Prefix 1, e.g 192.168.10.1/24] [IP Prefix 2, e.g. 192.168.20.1/24]

    OR the following commands are equivalent:
    ./scripts/sdxclient.sh -c client-config/alice.conf -e "link 192.168.10.1/24 192.168.20.1/24"

  9. [OPTIONAL]
    For sdx demo, I added scripts to automatically configure the routing table with quagga in client slice. These scripts depends on the IP addresses assigned to client slice, the topology of client slice, which node in client slice is stitched to sdx slice, and the gateway in sdx slice.
    
    Setup routing in client side:
    An example command of adding an entry to the routing table is as follows, this only supports dest IP address with /32 netmask
    Another way to do this is using Quagga with zebra enabled, and add routing entries in zebra.conf, dest ip with any netmask is supported
    
    In the demo, to enable communication between CNode1 in alice and CNode1 in bob, the commands are:
    CNode1-alice$ ip route add 192.168.20.2/32 via 192.168.10.1
    CNode0-alice$ ip route add 192.168.20.2/32 via 192.168.33.1
    CNode1-bob$  ip route add 192.168.10.2/32 via 192.168.20.1
    CNode0-bob$ ip route add 192.168.10.2/32 via 192.168.34.1

  10. Delete a slice
    We can delete a slice with command: ./scripts/createslice.sh -c configFile -d
    For exmaple: ./scripts/createslice.sh -c client-config/alice.conf -d


    ============Stitching External Sites (Chameleon, Duke, ESNet...) to Exogeni=============

[1] Run riak-server
    Launch a riak server with code and instructions in SAFE-Riak-Server

Configuration: set variables in "SAFE/configure" and run it. It will generate configuration files for sdx, alice and bob and put them in their config directory

[2] Create a SDX slice
  a) $cd SDX-Simple
  b) Edit configuration file for sdx slice "config/sdx.conf"
  c) build
     $./scripts/build.sh
  d) create SDX slice
     $./scritps/createslice.sh -c config/sdx.conf

[3] Run sdx server controller, configure the address and port number that sdx server will listen on ("config.serverurl").
     $./scripts/sdxserver.sh -c config/sdx.conf
     
     NOTE: when running HTTP server on ExoGENI, use serverurl=0.0.0.0:8080.

[4] Specify the ip address of safe server for Chameleon controller in SDX-Client-StitchPort/config/carrot.conf
    Configure the address of SDX server controller ("config.sdxserver") in SDX-Client-StitchPort/config/carrot.conf 

[5] Stitching Chameleon Node to  SDX slice
    1) First, create a Chameleon node, using vlan tag "3298"
    2) For Chameleon slice, we need another safe server for it (In this demo, we use the SAFE server in SDX slice for everything, therefore, this step is skipped). 
    3) Build chameleon controller:
       a) ./scripts/build.sh
       b) $ ./scripts/run.sh -c config/carol.conf
        >stitch http://geni-orca.renci.org/owl/ion.rdf#AL2S/Chameleon/Cisco/6509/GigabitEthernet/1/1 3298 [SDX_SLICE_NAME, e.g., sdx] c3 [Cameleon_Node_IP] 10.32.98.200/24
        OR
        $./scripts/run.sh -c config/carol.conf -e "stitch http://geni-orca.renci.org/owl/ion.rdf#AL2S/Chameleon/Cisco/6509/GigabitEthernet/1/1 3298 [SDX_SLICE_NAME, e.g., sdx] [STITCH_POINT, e.g., c3] 10.32.98.204 10.32.98.200/24"

[6] When stitching a chameleon node to exogeni node, we know the ip address of the chameleon node, say "10.32.98.204". 
    In the stitching request, we tell the sdx controller what IP address it should use for the new interface on c3 to the stitchport, we can specify any address in the same subnet as the chameleon node, say "10.32.98.200"
        The topology is like this:
        c0-c1-c2-c3 (10.32.98.200/24)----stitchport----(10.32.98.204/24)ChameleonNode
        Note that in sdx server, we use SDN controller to configure the ip address. The ip address is not configured for the physical interface, so we can't ping from exogeni node to chameleon node. But we can ping from the Chameleon node to the exogeni node.

      a) Chameleon slice advertises its ip prefixes in the same way as ExoGENI slice does
        $./scripts/run.sh -c config/carol.conf -e "route 10.32.98.1/24 10.32.98.204 [SDX_SLICE_NAME, e.g., sdx] [STITCH_POINT, e.g., c3]"
      b) Chameleon node set up routing table when it wants to talk with exogeni slices in different subnets

NOTE: Now we have "-n" option for sdx server and both clients, which can be used to DISABLE SAFE AUTHORIZATION



