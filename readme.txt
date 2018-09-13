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
        safekey         Principal ID (safe key hash) or the file name of safe key
        sshkey          the location of your sshkey, used for logging onto ExoGENI node
        exogenipem      Your ExoGENI pem file
        serverurl       The ip address and port number of vSDX slice controller
        scritpsdir      /Path/to/SDX/SDX-Simple/SAFE_SDX/src/main/resources/
        safe            Whether safe authorization is enabled or not
        safeserver      Ip address of safe server
        serverinslice   Whether safe server and plexus controller is in the exogeni slice
        plexusserver    IP address of plexus server

        Generally, you need to change the above configuration fields, and leave the rest alone.
        clientsites     the expected sites of clients where we need to launch some routers when we create the slice
        routernum       The number of routers to be launched when creating the slice
        controllersite  The site of plexus sdn controller
        sitelist        The list of sites that customer can request for stitching [Some of the sites may fail depending on the status]
        bw              The bandwidth of links between vSDX routers
        brobw           The bandwidth of links between routers and Bro node
        bro             whether deploy bro when creating the vsdx or not

   For demo with Both ExoGENI and Chameleon, [to be updated]

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
     $./scripts/sdx_exogeni_client.sh -c client-config/alice.conf
     $./scripts/sdx_exogeni_client.sh -c client-config/bob.conf

  [6]. alice stitch CNode0 to sdx/c0, in alice's controller, run:
    $>stitch CNode0 

    [optional, the name of router in vsdx slice if the client knows exactly which router it wants to stitch to, e.g., c0], this is useful
    when there is multiple routers on the same site
    bob stitch CNode0 to sdx/c3, in bob's controller run:
    $>stitch CNode0 c3
    
    OR the following commands are equivalent:
    ./scripts/sdxclient.sh -c client-config/alice.conf -e "stitch CNode0"
    ./scripts/sdxclient.sh -c client-config/bob.conf -e "stitch CNode0"

  [7]. advertise prefix
    alice tells sdx controller its address space
    $>route 192.168.10.1/24 192.168.33.2
    bob tells sdx controller its address space
    $>route 192.168.20.1/24 192.168.34.2
    
    OR the following commands are equivalent:
    ./scripts/sdx_exogeni_client.sh -c client-config/alice.conf -e "route 192.168.10.1/24 192.168.33.2"
    ./scripts/sdx_exogeni_client.sh -c client-config/bob.conf -e "route 192.168.20.1/24 192.168.34.2"

  [8] customer connection request
    $>link [IP Prefix 1, e.g 192.168.10.1/24] [IP Prefix 2, e.g. 192.168.20.1/24]

    OR the following commands are equivalent:
    ./scripts/sdx_exogeni_client.sh -c client-config/alice.conf -e "link 192.168.10.1/24 192.168.20.1/24"

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
1. Run sdx server controller, configure the address and port number that sdx server will listen on ("config.serverurl").
     $./scripts/sdxserver.sh -c config/sdx.conf
     
     NOTE: when running HTTP server on ExoGENI, use serverurl=0.0.0.0:8080.

2.  Stitching Chameleon Node to  SDX slice
    1) First, create a Chameleon node, using vlan tag "3298"
    2) For Chameleon slice, we need another safe server for it (In this demo, we use the SAFE server in SDX slice for everything, therefore, this step is skipped). 
    3) Build chameleon controller:
       b) $ ./scripts/sdx_stitchport_client.sh -c config/chameleon.conf
        >stitch http://geni-orca.renci.org/owl/ion.rdf#AL2S/Chameleon/Cisco/6509/GigabitEthernet/1/1 3298 [Cameleon_Node_IP] 10.32.98.200/24 [SDX_SITE_NAME] [Optional: sdx node name] 
        OR
        $./scripts/run.sh -c config/carol.conf -e "stitch http://geni-orca.renci.org/owl/ion.rdf#AL2S/Chameleon/Cisco/6509/GigabitEthernet/1/1 3298  10.32.98.204 10.32.98.200/24 [SDX_SITE_NAME] [STITCH_POINT, e.g., c3]"

[6] When stitching a chameleon node to exogeni node, we know the ip address of the chameleon node, say "10.32.98.204". 
    In the stitching request, we tell the sdx controller what IP address it should use for the new interface on c3 to the stitchport, we can specify any address in the same subnet as the chameleon node, say "10.32.98.200"
    Note that in sdx server, we use SDN controller to configure the ip address. The ip address is not configured for the physical interface, so we can't ping from exogeni node to chameleon node. But we can ping from the Chameleon node to the exogeni node.
      a) Chameleon slice advertises its ip prefixes in the same way as ExoGENI slice does [not tested]
        $./scripts/sdx_stitchport_client.sh -c config/carol.conf -e "route 10.32.98.1/24 10.32.98.204 [SDX_SLICE_NAME, e.g., sdx] [STITCH_POINT, e.g., c3]"
      b) Chameleon node set up routing table when it wants to talk with exogeni slices in different subnets

NOTE: Now we have "-n" option for sdx server and both clients, which can be used to DISABLE SAFE AUTHORIZATION

-------------- Bro Experiment --------------------
To run the bro experiment, we transmit a file with known signature via FTP service. The TestSlice code should have already set up FTP service when it creates the slice. 
1. create the test slice: [1] modify TestMain to call emulationSlice() in main(). [2] modify configuration files to specify two sites. Since we need large bandwdith between two sites. It's highly suggested to choose UH-TAMU, or any pair from (UFL, UNF, FIU). [3] run TestMain to create the emulation slice
2. Run BroMain for the experiment.
   ./scripts/bro.sh






