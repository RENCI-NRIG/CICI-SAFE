# sshd
#
# VERSION               0.0.1
FROM     ubuntu:16.10
MAINTAINER Bing Xie

#add ssh service
RUN apt-get update
RUN apt-get install -y openssh-server openssh-client rsync vim g++

# fix the 254 error code
RUN sed  -i "/^[^#]*UsePAM/ s/.*/#&/"  /etc/ssh/sshd_config
RUN echo "UsePAM no" >> /etc/ssh/sshd_config
RUN echo "Port 2122" >> /etc/ssh/sshd_config
RUN mkdir /var/run/sshd
#RUN echo 'root:0' | chpasswd
RUN sed -i 's/PermitRootLogin without-password/PermitRootLogin yes/' /etc/ssh/sshd_config


EXPOSE 60000-65000 10020 50000 50020 50090 50070 50010 50075 8020 8031 8032 8033 8040 8042 9000 49707 22 8088 8030 2122 8098 8087

#Riak Server
RUN echo "deb http://archive.ubuntu.com/ubuntu xenial main universe" >> /etc/apt/sources.list
RUN echo "deb http://archive.ubuntu.com/ubuntu xenial-updates main universe" >> /etc/apt/sources.list

#apt-get update && apt-get upgrade -y
RUN apt-get update -y

# Install openjdk-8
RUN apt-get install -y openjdk-8-jdk

# Install git
RUN apt-get install -y git curl make htop

# Assume the ssh private key has been properly setup before running this script
# Set up git private key
#scp qiang@152.3.136.26:~/.ssh/id_rsa   ~/.ssh/

# Install scala
ENV SCALA_VERSION 2.11.8
RUN echo "export SCALA_VERSION=$SCALA_VERSION" >> /root/.bashrc

RUN cd /root && wget http://www.scala-lang.org/files/archive/scala-$SCALA_VERSION.tgz
RUN (cd /root && gunzip < scala-$SCALA_VERSION.tgz)|(cd /opt && tar -xvf -)

ENV export SCALA_HOME /opt/scala-$SCALA_VERSION
RUN echo "export SCALA_HOME=/opt/scala-$SCALA_VERSION" >> /root/.bashrc

ENV export PATH $SCALA_HOME/bin:$PATH
RUN echo "export PATH=$SCALA_HOME/bin:\$PATH" >> /root/.bashrc

# Install sbt

ENV SBT_VERSION 0.13.12
RUN echo "export SBT_VERSION=$SBT_VERSION" >> /root/.bashrc

RUN cd /root && wget https://dl.bintray.com/sbt/native-packages/sbt/$SBT_VERSION/sbt-$SBT_VERSION.tgz
RUN (cd /root && gunzip < sbt-$SBT_VERSION.tgz)|(cd /opt && tar -xvf -)

# Better naming of the dir when necessary
RUN mv /opt/sbt  /opt/sbt-$SBT_VERSION

RUN echo export SBT_HOME=/opt/sbt-$SBT_VERSION
RUN echo "export SBT_HOME=/opt/sbt-$SBT_VERSION" >> /root/.bashrc

RUN export PATH=$SBT_HOME/bin:$PATH
RUN echo "export PATH=$SBT_HOME/bin:\$PATH" >> /root/.bashrc


# Install randome useful stuff
RUN apt-get install -y libssl1.0.0
RUN apt-get install -y libpam0g-dev
RUN apt-get install -y libssl-dev

# git clone safe repo
#RUN git clone git@gitlab.oit.duke.edu:silver/super-safe.git 

#RUN export SAFE_HOME="/root/safe"

# Download and install Riak
RUN wget http://s3.amazonaws.com/downloads.basho.com/riak/2.1/2.1.4/ubuntu/trusty/riak_2.1.4-1_amd64.deb
RUN apt-get install -y logrotate sudo net-tools
RUN dpkg -i riak_2.1.4-1_amd64.deb

# Configure Riak: let it listen on ports of the internal ip address (10.*.*.*), and customize its nodename
COPY start-riak.sh /root
RUN ifconfig
RUN /bin/sh /root/start-riak.sh
#RUN internal_ip=`ifconfig|grep ens3 -A1|grep "10\."|awk '{print $2}'|sed 's/addr://'` && sed -i "/^listener.http.internal/ s:.*:listener.http.internal = ${internal_ip}\:8098:" /etc/riak/riak.conf &&  sed -i "/^listener.protobuf.internal/ s:.*:listener.protobuf.internal = ${internal_ip}\:8087:" /etc/riak/riak.conf &&  sed -i "/^nodename = / s:.*:nodename = riak@${internal_ip}:" /etc/riak/riak.conf
#
#ENV WAIT_FOR_ERLANG 30
## Start and manage Riak
#RUN riak start
##[warns about ulimit -n is 1024]
#
##riak attach
##[attaches to the server and runs riak console]
##[console doesn't quit properly, must ctrl-C it]
##[but I think that kills the server, had to restart it.]
#
#RUN riak ping
#
## Try put and get
##curl -XPUT http://localhost:8098/buckets/welcome/keys/german   -H 'Content-Type: text/plain'   -d 'herzlich willkommen'
##curl http://localhost:8098/buckets/welcome/keys/german
## [herzlich willkommenroot] 
#
#
## Set up riak bucket for certificate storage
#RUN  riak-admin bucket-type create safesets '{"props":{"n_val":1, "w":1, "r":1, "pw":1, "pr":1}}'
#RUN  riak-admin bucket-type activate  safesets
#RUN  riak-admin bucket-type update safesets '{"props":{"allow_mult":false}}'


#RUN echo 'ubuntu:0' | chpasswd
CMD    /usr/sbin/sshd -D

