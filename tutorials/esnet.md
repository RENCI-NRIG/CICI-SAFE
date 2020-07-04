The is the tutorial for running ExoPlex on ESNet VFCs to connect Chameleon networks and ExoGENI networks. In this tutorial, we will run ExoPlex on VFC network in ESNet and connect customer networks in Chameleon@TACC, Chameleon@UC and ExoGENI slice@RENCI. In this tutorial, we are not authorizing requests for stitching and connections with SAFE, as stitching operations are only performed mannually with privileges, and the authorizaiton policies and process are similar to the other demos.

### 1. Set up the network topology.
The topology of the network in this tutorial is as follows.

                          Chameleon@UC
                               |
                               | vlan: 3296
                              STAR
                              /  \
                             /    \
                 vlan:3505  /      \    3800
Chameleon@TACC------------ ATLA----WASH--------ExoGENI@RENCI

##### (a) VFC network
Creating VFC networks in ESnet and stitching Chameleon networks or ExoGENI slices to ESnet are privileged. Thanks to Mert for setting up this.

##### (b) Chameleon network
We will create a Chameleon network at TACC, a Chameleon network at UC. On each site, create a VFC, with a "physnet1" network and an "exogeni" stitchable network, and attach a compute node to the "physnet1" network. Jupyter scripts for creating networks in Chameleon are in *CICI-SAFE/Chameleon-Jupyter/tutorials/MixedNetworks-TACC.ipynb*. The Chamleon network @TACC and Chameleon network@UC should have **different subnets(cidr)**, as we are running ESnet VFCs as routers. 

In this tutorial, Chameleon network @TACC has the subnet *192.168.110.0/24* and Chameleon network @UC has the subnet *192.168.100.0/24*. The compute nodes at TACC and UC have IP address 192.168.110.239 and 192.168.100.156 respectively.

##### (c) ExoGENI slice
Create an ExoGENI slice with a node in RENCI connected to another node in another site. The ExoGENI slice should have a different subnet as well.

In this tutorial, the ExoGENI slice has the subnet *192.168.10.0/24* and the node has IP address *192.168.10.2*.

##### (d) Stitch Chamleon networks and ExoGENI slices to ESnet (privileged operations)

                              
### 2. Set up SDN controllers for ESNet VFCs.
For ESnet VFCs, we run ryu/rest_router based application **vfc_router**. vfc_router adds support for source IP prefix in forwarding rules and adapts to Corsa switch VFCs (e.g. synthesize MAC addresses for ports on the VFCs).
SDN controllers are deployed in containers provided by ESNet. Besides vfc_router, we are also running **ofctl_rest** application to support queries and resetting the VFC (more details in step 7).

In the containers for SDN controllers, get vfc_router.py and ofctl_rest.py from *CICI-SAFE/ryu-apps* and copy to /opt/:

        cp vfc_router.py /opt/
        cp ofctl_rest.py /opt/

Start SDN controller with the script:

        RYU_DIR="/opt/ryu"
        RYU_REST_APP="ofctl_rest.py"
        RYU_PID_FILE="/var/run/ryu/ryu-manager.pid"
        RYU_LOG_FILE="/opt/log/ryu/ryu-manager.log"
        RYU_CONFIG_DIR="/opt/ryu/etc"
        RYU_APP="/opt/vfc_router.py"
        #RYU_APP="/opt/simple_switch_13_custom_chameleon.py"
        RYU_REST="/opt/ofctl_rest.py"
        OFP_TCP_LISTEN_PORT="6653"

        /usr/bin/ryu-manager --pid-file ${RYU_PID_FILE} --ofp-tcp-listen-port ${OFP_TCP_LISTEN_PORT} --log-file ${RYU_LOG_FILE} --app-lists ${RYU_REST} ${RYU_APP}

### 3. Set up SDN controllers for Chameleon VFCs.
For Chameleon VFCs, we are running them as learning switches with *simple_switch_13_custom_chameleon.py*. It works tranparantly as if the nodes in Chameleon are directly connected to the ESNet VFCs. The public IP address and port number for the SDN controller should be specified when creating VFCs with Jupyter notebooks.

        RYU_APP="/opt/simple_switch_13_custom_chameleon.py"
        RYU_REST="/opt/ofctl_rest.py"
        OFP_TCP_LISTEN_PORT="6653"

        /usr/bin/ryu-manager --pid-file ${RYU_PID_FILE} --ofp-tcp-listen-port ${OFP_TCP_LISTEN_PORT} --log-file ${RYU_LOG_FILE} --app-lists ${RYU_REST} ${RYU_APP}

### 4. Run ExoPlex in "jumpbox".
The SDN controllers for ESnet VFCs are only accessible from the jumpbox
(the VM that has access to the controller containers for ESNet VFCs). Since ExoPlex needs to call SDN controllers for network configurations, we must run ExoPlex server in the jumpbox.

##### (a) Install Orca5, ahab1.7. Download CICI-SAFE repository and compile.
The detailed instructions can be found in **readme.md**

##### (b) Save ESnet VFC topology in a json file.
ExoPlex loads the topology information (i.e. VFCs, links, stitch VLANs, OpenFlow controller addresses) for the ESnet VFC network from a json configuration file. The topology of the network in this tutorial is saved in *CICI-SAFE/exoplex/vfc-config/esnet.json*. Note taht the dpid of the VFC should be in **hexadecimal**. We can learn the dpid by running vfc_router controller, there will be messages about dpid and ports when the VFC is connected to the controller.

        [
            {
                "router": {
                        "name": "vfc-esnet-atla",
                        "dpid": "0000ce045cc7b141",
                        "site": "ATLA",
                        "controller":"192.168.120.122:8080"
                }
            },
            {
                "router": {
                        "name": "vfc-esnet-star",
                        "dpid": "0000f2e345426c44",
                        "site": "STAR",
                        "controller":"192.168.120.15:8080"
                }
            },
            {
                "router": {
                        "name": "vfc-esnet-wash",
                        "dpid": "0000d652c7eac84e",
                        "site": "WASH",
                        "controller":"192.168.120.123:8080"
                }
            },
            {
                "link": {
                        "name": "link0",
                        "node1": "vfc-esnet-atla",
                        "node2": "vfc-esnet-star"
                }
            },
            {
                "link": {
                        "name": "link1",
                        "node1": "vfc-esnet-atla",
                        "node2": "vfc-esnet-wash"
                }
            },
            {
                "link": {
                        "name": "link2",
                        "node1": "vfc-esnet-star",
                        "node2": "vfc-esnet-wash"
                }
            },
            {
                "stitch": {
                        "name": "net-atlastitch",
                        "router": "vfc-esnet-tacc",
                        "site": "ATLA",
                        "vlan": "3505"
                }
            },
            {
                "stitch": {
                        "name": "net-washstitch",
                        "router": "vfc-esnet-wash",
                        "site": "WASH",
                        "vlan": "3800"
                }
            },
            {
                "stitch": {
                        "name": "net-starstitch",
                        "router": "vfc-esnet-uc",
                        "site": "STAR",
                        "vlan": "3296"
                }
            }
        ]

##### (c) Set the path to the ESnet VFC topology file in ExoPlex config file
In this tutorial, the ExoPlex config file is *CICI-SAFE/exoplex/vfc-config/vfc.conf*.

        config {
          slicename="esnet-vfc"
          type="server"
          ipprefix="192.168.128.1/24"
          safe=false
          plexusinslice=false
          serverurl="http://0.0.0.0:8888/"
          topofile="~/CICI-SAFE/exoplex/vfc-config/esnet.json"
        }


##### (d) Run ExoPlex server.
    
        cd CICI-SAFE/exoplex
        ./scripts/vfcserver.sh -c vfc-config/vfc.conf

### 5. Request for Stitching and Connection for customer networks.
Though stitching operations are already completed, we still call the stitching API of ExoPlex to pass the stitching properties(gateway address).

##### (a) Stitch and advertise prefix for Chamleon network at TACC
The config file is *CICI-SAFE/exoplex/chameleon-config/c1.conf*

        config{
            slicename="alice"
            type="client"
            safe=false
            serverurl="http://127.0.0.1:8888/"
        }

Stitch request with stitching properties. IP address *192.168.110.11/24* will be assigned to the VFC interface. *192.168.110.239* is the IP address of the client node.
    
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c1.conf -e "stitchvfc ATLA 3505 192.168.110.239 192.168.110.11/24"

Advertise prefix for Chameleon network at TACC

        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c1.conf -e "route 192.168.110.0/24 192.168.110.239"

##### (b) Stitch and advertise prefix for Chamleon network at UC

        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c2.conf -e "stitchvfc STAR 3296 192.168.100.156 192.168.100.11/24"
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c2.conf -e "route 192.168.100.0/24 192.168.100.156"

##### (c) Stitch and advertise prefix for ExoGENI slice at RENCI
Since the ExoGENI slice is also stitched mannually, there is no difference between ExoGENi client network and Chameleon network. The commands and scripts are the same for ExoGENI client network.
        
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c3.conf -e "stitchvfc WASH 3800 192.168.10.2 192.168.10.20/24"
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c3.conf -e "route 192.168.10.0/24 192.168.10.2"

##### (d) Request for connections between subnets
Chameleon network at TACC:
        
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c1.conf -e "link 192.168.110.0/24 192.168.100.0/24"
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c1.conf -e "link 192.168.110.0/24 192.168.10.0/24"

Chameleon network at UC:
        
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c2.conf -e "link 192.168.100.0/24 192.168.110.0/24"
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c2.conf -e "link 192.168.100.0/24 192.168.10.0/24"

ExoGENI network at RENCI:

        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c3.conf -e "link 192.168.10.0/24 192.168.110.0/24"
        ./scripts/sdx_stitchport_client.sh -c chameleon-config/c3.conf -e "link 192.168.10.0/24 192.168.100.0/24"

### 6. Set up routing for experimental subnets in client nodes.
With **quagga** or "ip route add" command. For example, in this turoial, we can run the following commands.

Client node at TACC:

        ip route add 192.168.0.0/16 via 192.168.110.11

Client node at UC:
        
         ip route add 192.168.0.0/16 via 192.168.110.11

Client node at RENCI:
    
        ip route add 192.168.0.0/16 via 192.168.10.11



### 6. Check connectivity between subnets.
Now we should be able to ping between the three subnets. It takes some time(5 to30 seconds) for the $ping$ request to succeed. This is due to the nature of ryu/rest_router application. It needs to learn MAC addresses along the path before two client nodes can talk.

### 7. Resetting the network.
We may want to reset the VFC network for another run of the process.

##### (a) Stop ExoPlex server.
##### (b) Clear flows in VFCs.
Make calls to ofctl_rest APIs to delete all flows on the VFCs. Note that the dpid of the VFCs should be in **decimal**(converted from the dpid in hexadecimal). 

        curl -X DELETE http://192.168.120.122:8080/stats/flowentry/clear/226518131781953
        curl -X DELETE http://192.168.120.15:8080/stats/flowentry/clear/267057933478980
        curl -X DELETE http://192.168.120.123:8080/stats/flowentry/clear/235651029715022

##### (c) Restart SDN controllers for ESnet VFCs


### 8. Debugging when it fails
If the connectivity fails, steps of debugging:

##### (a) check the routing table of client nodes.
An example routing table from client node at TACC

        Destination     Gateway         Genmask         Flags Metric Ref    Use Iface
        0.0.0.0         192.168.110.1   0.0.0.0         UG    0      0        0 eno1
        169.254.169.254 192.168.110.2   255.255.255.255 UGH   0      0        0 eno1
        192.168.0.0     192.168.110.11  255.255.0.0     UG    0      0        0 eno1
        192.168.110.0   0.0.0.0         255.255.255.0   U     0      0        0 eno1

##### (b) Check connectivity between client nodes and ESNet VFCs. 
The connecivity between client nodes and ESNet VFCs might fail due to failed links or incorrect SDN controller. To verify if the client node can reach the VFC. Print the **arp** table, if the **HW(MAC)** address of the VFC gateway address is not empty, the connection between the client node and the VFC is good. An example **good** arp table from client node at TACC
        
        Address                  HWtype  HWaddress           Flags Mask            Iface
        host-192-168-110-1.open  ether   fa:16:3e:65:76:41   C                     eno1
        host-192-168-110-2.open  ether   fa:16:3e:d1:99:e8   C                     eno1
        192.168.110.11           ether   fa:16:3e:00:a2:1a   C                     eno1

When the connection doesn't work, the HWaddress for the gateway address would be empty.

Another way to verify the connectivity client node and ESnet VFC is by "ping". Ping from the client node to the VFC interface address. From the console of the SDN controller, there will be messages about arp request and arp replies from and to the client node, which means the connection is good. **Note** that there will never be ICMP replies from the VFC interface to the client node, Corsa switch is swallowing the ICMP reply that the SDN controller generated.

##### (c) Check the log of ExoPlex controller
Check the output to the console. Check the log file in *CICI-SAFE/exoplex/log/exoplex.log*, look for results of calls to SDN controllers to see if there are any errors.

##### (d) Verify the flow tables of the VFCs.
Dump the flows via ofctl_rest API

        curl -X GET http://192.168.120.15:8080/stats/flow/267057933478980 >starflows.txt
        curl -X GET http://192.168.120.122:8080/stats/flow/226518131781953 >atlaflows.txt
        curl -X GET http://192.168.120.123:8080/stats/flow/235651029715022  >washflows.txt

Use the following python scripts to parse the flows into a readable format:
        
        import json
        import sys

        with open(sys.argv[1]) as json_file:
            data = json.load(json_file)
            for d in data:
                sub = data[d]
                for d1 in sub:
                    print(d1)




        
