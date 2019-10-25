# Connect exogeni slices with networks in Chameleon

## 1. Create VFC with ExoGENI-stitchable networks

Run SDN controller,

    ryu-manager --ofp-tcp-listen-port 6653 ~/CICI-SAFE/ryu-apps/vfc_router.py ~/CICI-SAFE/ryu-apps/ofctl_rest.py

## 2. Create ExoGENI client networks
        
        ./scripts/createclientslice.sh -c client-config/alice.conf

## 3. Save topology of the VFC in json file. For exmaple,


        [
            {
                "router": {
                        "name": "vfc-1",
                        "dpid": "0000fe754c80b54d",
                        "site": "UC"
                }
            },
            {
                "stitch": {
                        "name": "net-exogeni-sc19-safe1",
                        "router": "vfc-1",
                        "site": "UC",
                        "vlan": "3295"
                }
            },
            {
                "stitch": {
                        "name": "net-exogeni-sc19-safe2",
                        "router": "vfc-1",
                        "site": "UC",
                        "vlan": "3293"
                }
            }
        ]

## 4. Start vfc server

        ./scripts/vfcserver.sh -c vfcconfig/vfc.conf

## 5. Stitch ExoGENI client slice to VFC


        ./scripts/sdx_exogeni_client.sh -c client-config/vfc/c0.conf -e "stitchvfc CNode0 UC 3293 192.168.10.2 192.168.10.1/24"

        ./scripts/sdx_exogeni_client.sh -c client-config/vfc/c1.conf -e "stitchvfc CNode0 UC 3295 192.168.20.2 192.168.20.1/24"

## 6. Client networks advertise prefix

        ./scripts/sdx_exogeni_client.sh -c client-config/vfc/c0.conf -e "route 192.168.10.1/24 192.168.10.2"

        ./scripts/sdx_exogeni_client.sh -c client-config/vfc/c1.conf -e "route 192.168.20.1/24 192.168.20.2"

## 7. Client networks request for connection

        ./scripts/sdx_exogeni_client.sh -c client-config/vfc/c0.conf -e "link 192.168.10.1/24 192.168.20.1/24"

        ./scripts/sdx_exogeni_client.sh -c client-config/vfc/c1.conf -e "link 192.168.20.1/24 192.168.10.1/24"
