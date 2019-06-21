# Exoplex
### 1. Compile code
The java code is compiled with JDK8.
1. Install Orca5 (https://github.com/RENCI-NRIG/orca5)
2. Install ahab1.7 (https://github.com/RENCI-NRIG/ahab)
3. Compile the code

        cd CICI-SAFE/exoplex
        mvn  clean package appassembler:assemble -DskipTests

### 2. Update configuration files

#####   About configuration files in SDX-Simple/config and SDX-Simple/client-config:.
        slicename:    Name of the slice
        safekey:         Principal ID (safe key hash) or the file name of safe key 
        sshkey:          the location of your sshkey, used for logging onto ExoGENI node
        exogenipem:      Your ExoGENI pem file
        serverurl:       The ip address and port number of vSDX slice controller
        scritpsdir:      /Path/to/SDX/SDX-Simple/SAFE_SDX/src/main/resources/
        safe:            Whether safe authorization is enabled or not 
        safeserver:      Ip address of safe server
        serverinslice:   Whether safe server and plexus controller is in the exogeni slice
        plexusserver:    IP address of plexus server
##### Generally, you need to change the above configuration fields, and leave the rest alone.
        clientsites:     the expected sites of clients where we need to launch some routers when we create the slice
        routernum:       The number of routers to be launched when creating the slice
        controllersite:  The site of plexus sdn controller
        sitelist:        The list of sites that customer can request for stitching [Some of the sites may fail depending on the status]
        bw:              The bandwidth of links between vSDX routers
        brobw:           The bandwidth of links between routers and Bro node
        bro:             whether deploy bro when creating the vsdx or not 

### 3. Run individual SDX and clients

##### Run SDX

[1]. To run the SDX demo, first we creat a SDX slice on exogeni.
  a) Edit configuration file for sdx slice "config/sdx.conf"
  b) build the code

        ./scripts/build.sh

  c) create SDX slice

        ./scritps/createslice.sh -c config/sdx.conf

[2] Configure the address and port number that sdx server will listen on ("config.serverurl"). Start sdx server

        ./scripts/sdxserver.sh -c config/sdx.conf

###### Run clients
[1] Create customer slices
   a) Edit configuration files in client-config
   b) build

        ./scripts/build.sh

   d) create alice slice and bob slice

        ./scripts/createclientslice.sh -c client-config/alice.conf

[2] Configure the address of SDX server controller ("config.sdxserver") in configuration files and run controller for Alice

        ./scripts/sdx_exogeni_client.sh -c client-config/alice.conf

    Alice stitch CNode0 to sdx, in alice's controller, run:
        stitch CNode0 192.168.10.2 192.168.10.1/24
    The first IP address without netmask is the address of the interface in customer network. Sdx will communicate with the customer using this address as gateway
    The second IP address with netmask is the address of the interface in SDX slice, the netmask is required.
    
[3] The following commands are equivalent:
    ./scripts/sdx_exogeni_client.sh -c client-config/bob.conf -e "stitch CNode0 192.168.10.2 192.168.10.1/24"

[4] With safe authorization enabled, to enable stitch to a customer slice. First we need to set up delegations to the user and slice with AuthorityMock:

    ./scripts/auth.sh customerkeyfile customerslice customerIPPrefix safeServerIp

The customer key file is the name of the customer's safe key (which should be put in the safe server container). For the demo, available keys are "key_p5, key_p6, key_p7,....."
The customerslice is the name of the customer slice
The customerIpPrefix is the Ip prefix of the customer network
The safeserverIP is the IP address of the safe server container

[5] Advertise prefix. Alice tells sdx controller its address space

        route 192.168.10.1/24 192.168.10.2
    
[6] The following commands are equivalent:

        ./scripts/sdx_exogeni_client.sh -c client-config/alice.conf -e "route 192.168.10.1/24 192.168.10.2"

[7] customer connection request

        link [IP Prefix 1, e.g 192.168.10.1/24] [IP Prefix 2, e.g. 192.168.20.1/24]

[8] The following commands are equivalent:

        ./scripts/sdx_exogeni_client.sh -c client-config/alice.conf -e "link 192.168.10.1/24 192.168.20.1/24"

[9] Undo stitching for exogeni client slice. Run SDX exogeni client to undo stitching. Use the command "unstitch nodename". For example:

        ./scripts/sdx_exogeni_client.sh -c client-config/alice.conf -e "unstitch CNode0"

 This operation will undo the stitching, delete the broadcast link in Sdx slice, revoke all IP prefixes advertised with the stitching client node as gateway, and delete all routes related with the prefix. After undoing the stitching, SDX server can keep runnning and there is no need to restart plexus controller

[10]. [OPTIONAL]
    For sdx demo, I added scripts to automatically configure the routing table with quagga in client slice. These scripts depends on the IP addresses assigned to client slice, the topology of client slice, which node in client slice is stitched to sdx slice, and the gateway in sdx slice.
    
    Setup routing in client side:
    An example command of adding an entry to the routing table is as follows, this only supports dest IP address with /32 netmask
    Another way to do this is using Quagga with zebra enabled, and add routing entries in zebra.conf, dest ip with any netmask is supported
    
    In the demo, to enable communication between CNode1 in alice and CNode1 in bob, the commands are:
    CNode1-alice$ ip route add 192.168.20.2/32 via 192.168.10.1
    CNode0-alice$ ip route add 192.168.20.2/32 via 192.168.33.1
    CNode1-bob$  ip route add 192.168.10.2/32 via 192.168.20.1
    CNode0-bob$ ip route add 192.168.10.2/32 via 192.168.34.1

[11]. Delete a slice
    We can delete a slice with command: ./scripts/createslice.sh -c configFile -d

    ./scripts/createslice.sh -c client-config/alice.conf -d


### titching External Sites (Chameleon, Duke, ESNet...) to Exogeni
1. Run sdx server controller, configure the address and port number that sdx server will listen on ("config.serverurl").

     ./scripts/sdxserver.sh -c config/sdx.conf

2.  Stitching Chameleon Node to  SDX slice
    1) First, create a Chameleon node, using vlan tag "3298"
    2) For Chameleon slice, we need another safe server for it (In this demo, we use the SAFE server in SDX slice for everything, therefore, this step is skipped). 
    3) Stitch chameleon node to exogeni slice

                ./scripts/sdx_stitchport_client.sh -c config/chameleon.conf
                >stitch http://geni-orca.renci.org/owl/ion.rdf#AL2S/Chameleon/Cisco/6509/GigabitEthernet/1/1 3298 [Cameleon_Node_IP] 10.32.98.200/24 [SDX_SITE_NAME] [Optional: sdx node name] 
OR

        ./scripts/sdx_stitchport_client.sh -c config/carol.conf -e "stitch http://geni-orca.renci.org/owl/ion.rdf#AL2S/Chameleon/Cisco/6509/GigabitEthernet/1/1 3298  10.32.98.204 10.32.98.200/24 [SDX_SITE_NAME] [STITCH_POINT, e.g., c3]"

[6] When stitching a chameleon node to exogeni node, we know the ip address of the chameleon node, say "10.32.98.204". 
    In the stitching request, we tell the sdx controller what IP address it should use for the new interface on c3 to the stitchport, we can specify any address in the same subnet as the chameleon node, say "10.32.98.200"
    Note that in sdx server, we use SDN controller to configure the ip address. The ip address is not configured for the physical interface, so we can't ping from exogeni node to chameleon node. But we can ping from the Chameleon node to the exogeni node.
    a) Chameleon slice advertises its ip prefixes in the same way as ExoGENI slice does [not tested]

        ./scripts/sdx_stitchport_client.sh -c config/carol.conf -e "route 10.32.98.1/24 10.32.98.204 [SDX_SLICE_NAME, e.g., sdx] [STITCH_POINT, e.g., c3]"

    b) Chameleon node set up routing table when it wants to talk with exogeni slices in different subnets

NOTE: Now we have "-n" option for sdx server and both clients, which can be used to DISABLE SAFE AUTHORIZATION





