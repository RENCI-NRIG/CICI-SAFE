#!/bin/bash

# The following code assumes that ubuntu 14.04 is pre-installed on the vm 
# This code will download and install Riak, as well as the prerequisite packages

# Configure Riak: let it listen on ports of the internal ip address (10.*.*.*), and customize its nodename
internal_ip=`ifconfig|grep eth0 -A1|grep "172\."|awk '{print $2}'|sed 's/addr://'`
sed -i "/^listener.http.internal/ s:.*:listener.http.internal = ${internal_ip}\:8098:" /etc/riak/riak.conf
sed -i "/^listener.protobuf.internal/ s:.*:listener.protobuf.internal = ${internal_ip}\:8087:" /etc/riak/riak.conf
sed -i "/^nodename = / s:.*:nodename = riak@${internal_ip}:" /etc/riak/riak.conf
cat /etc/riak/riak.conf
echo $internal_ip


# Start and manage Riak
sudo riak start
#[warns about ulimit -n is 1024]

#sudo riak attach
#[attaches to the server and runs riak console]
#[console doesn't quit properly, must ctrl-C it]
#[but I think that kills the server, had to restart it.]

sudo riak ping

# Try put and get
#curl -XPUT http://localhost:8098/buckets/welcome/keys/german   -H 'Content-Type: text/plain'   -d 'herzlich willkommen'
#curl http://localhost:8098/buckets/welcome/keys/german
# [herzlich willkommenroot] 


# Set up riak bucket for certificate storage
sudo riak-admin bucket-type create safesets '{"props":{"n_val":1, "w":1, "r":1, "pw":1, "pr":1}}'
sudo riak-admin bucket-type activate  safesets
sudo riak-admin bucket-type update safesets '{"props":{"allow_mult":false}}'

