#!/bin/bash

# Configure Riak: let it listen on ports of the internal ip address (192.*.*.*), and customize its nodename
internal_ip=`ifconfig|grep eth0 -A1|grep "192\."|awk '{print $2}'|sed 's/addr://'`
sed -i "/^listener.http.internal/ s:.*:listener.http.internal = ${internal_ip}\:8098:" /etc/riak/riak.conf
sed -i "/^listener.protobuf.internal/ s:.*:listener.protobuf.internal = ${internal_ip}\:8087:" /etc/riak/riak.conf
sed -i "/^nodename = / s:.*:nodename = riak@${internal_ip}:" /etc/riak/riak.conf

# Initialize Riak with a SAFE bucket
riak start
riak ping
riak-admin bucket-type create safesets '{"props":{"n_val":1, "w":1, "r":1, "pw":1, "pr":1}}'
riak-admin bucket-type activate safesets
riak-admin bucket-type update safesets '{"props":{"allow_mult":false}}'

/bin/bash
