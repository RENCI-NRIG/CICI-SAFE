#!/bin/bash

#apt-get update;
#apt-get -y install openvswitch-switch;
#apt-get -y install iperf;
#/etc/init.d/neuca stop;
#check if there is a bridge, if yes skip
# if a new interface is not added to the ovsbridge, then add it and set controller again
manageif=$(ifconfig -a| grep -B1 "inet 10.\|inet addr:10." | awk '$1!="inet" && $1!="--" {print $1}')

#ifconfig -a|grep "eno\|ens\|eth"|grep -v "$manageif"|sed 's/[ :\t].*//;/^$/d'
#ifconfig -a|grep "eno\|ens\|eth"|grep -v "$manageif"| sed 's/Link.*HWaddr //'
ifconfig -a|grep "eno\|ens\|eth"|sed 's/HWaddr/\nHWaddr/' | sed 'N;s/\n/ /'|grep -v "$manageif"| sed 's/Link.*HWaddr //'| sed 's/:.*flags.*ether / /'| sed 's/\s*txqueuelen.*(Ethernet)//'

