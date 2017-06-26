#!/bin/bash
dpid=$(ovs-ofctl show br0 | grep "dpid:" |cut -d: -f3)
# #1 the ip and port  number of controller Restful API $2 the id of router
num=$(ifconfig |grep  "eth"|grep -v "eth0"|grep -c "eth")
#curl -X POST -d '{"routerid":"'"$2"'","interfaces":"'"$num"'"}' $1/router/$dpid
echo "$num"' '"$dpid"
