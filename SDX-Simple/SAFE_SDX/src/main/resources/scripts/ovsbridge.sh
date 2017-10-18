#!/bin/bash

#apt-get update;
#apt-get -y install openvswitch-switch;
#apt-get -y install iperf;
#/etc/init.d/neuca stop;
#check if there is a bridge, if yes skip
# if a new interface is not added to the ovsbridge, then add it and set controller again
string=$(ifconfig -a);
if [[ $string == *"br0"* ]]; then
  echo "br0 is there"
else
  ovs-vsctl add-br br0
  ovs-vsctl set Bridge br0 protocols=OpenFlow10,OpenFlow11,OpenFlow12,OpenFlow13
  ovs-vsctl set-manager ptcp:6632
  ovs-vsctl set-controller br0 tcp:$1
fi

interfaces=$(ifconfig -a|grep "eth"|grep -v "eth0"|sed 's/[ \t].*//;/^$/d')

brinterfaces=$(ovs-ofctl show br0)

newport=0
while read -r line; do
  if [[ $brinterfaces == *"$line"* ]]; then
    echo "it's there"
  else
    ifconfig $line up
    ifconfig $line 0
    ovs-vsctl add-port br0 $line
    ovs-vsctl del-controller br0
    ovs-vsctl set-controller br0 tcp:$1
  fi
done <<< "$interfaces"


