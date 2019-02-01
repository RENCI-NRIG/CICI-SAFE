#!/bin/bash

#apt-get update;
#apt-get -y install openvswitch-switch;
#apt-get -y install iperf;
#/etc/init.d/neuca stop;
string=$(ifconfig -a);
if [[ $string == *"br0"* ]]; then
  echo "br0 is there"
else
  ovs-vsctl add-br br0
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
    if [[ $vsinterfaces == *"$line"* ]]; then
        ovs-vsctl del-port br0 $line
        ovs-vsctl add-port br0 $line
    else
        ovs-vsctl add-port br0 $line
    fi
   fi
done <<< "$interfaces"


