#!/bin/bash

#setup condor

CWD=`pwd`
chmod 666 /etc/hosts

adduser condor

mkdir /home/condor/.ssh

echo "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCpLETezO6hUHgiLjHPEXXN6kkV9vBFqYAc4ha6OOoUYztx66mC3Sb590DZvn1wbUFZTJHMqRVG4x08MAsNBpBnuFsGQCg7Rw7cpW8uA20kJpAAUFtTJZO+gSu41QFMSLpX34tTqXBC7HMzmHZOGPtMzgt8fj2IhkZXq7o3mFWDct0GM7j5ShT3nzFkG8FTalLhPk/htRu3XYojOuWZJoVS0ZGVCkHuP2IJ0EzFEhDaplrXTVTujIEOpdCehlupFcWTCaZ/p8vil646M+JYX9pj6ijihn1XfC/c0w3cO+3neOaBhA+bvISvELI/JOcuUfCHsTv626Fpjw59wv6VcInH condor@Node0" >> /home/condor/.ssh/authorized_keys

echo ""  > /home/condor/.ssh/id_rsa
cat > /home/condor/.ssh/id_rsa  <<EOF
-----BEGIN RSA PRIVATE KEY-----
MIIEogIBAAKCAQEAqSxE3szuoVB4Ii4xzxF1zepJFfbwRamAHOIWujjqFGM7ceup
gt0m+fdA2b59cG1BWUyRzKkVRuMdPDALDQaQZ7hbBkAoO0cO3KVvLgNtJCaQAFBb
UyWTvoEruNUBTEi6V9+LU6lwQuxzM5h2Thj7TM4LfH49iIZGV6u6N5hVg3LdBjO4
+UoU958xZBvBU2pS4T5P4bUbt12KIzrlmSaFUtGRlQpB7j9iCdBMxRIQ2qZa101U
7oyBDqXQnoZbqRXFkwmmf6fL4peuOjPiWF/aY+oo4oZ9V3wv3NMN3Dvt53jmgYQP
m7yErxCyPyTnLlHwh7E7+tuhaY8OfcL+lXCJxwIDAQABAoIBADgng6zZJZTSWy4t
W0c6qnnxfNUXpOXav7XWrmieH8Uos0C7UwcnVZq/of0lKAo7meeEbRkcPv3KwZeK
8wAd360uGrjWbwROL/a5y0/gv0eyrTYNdmMBJCumQNcXjVi/A2vLvjnFEoiEaDEG
OK7vx+rUside2BoLSCotzKBLpob8/YyEM0HrKUw/R31SNCKg3IoP49RApXj3ReEk
8Jry3CzR48vjzu2TnZ74V/V4IN+goehcRTNHX00k6eQj7GhYV5b0TdblAaXZcKjB
809PQHrUHS5Ub21KB1o4NyQkqIfDJ1R8jdYxrJniFp2fCfW9oj8xv53qmM4B7dXo
zDWThDECgYEA0GyBNdZNAWbb5e+3hYomyVT0H4N0iyT9MARG3k9SF+TJOtdrWxH9
2FNXuMiktD/abXp8Coqy0RpsdgyDnpoVr7R0l5jULtTBFx/xrZRNoryHpb/QbuZ7
fKdWgEDjmFphB1clXUz5Bj0Y+8nIwEns18ULSswGOhzRe9fsM1xiofMCgYEAz8oX
D27K9EUKhHmewAxUVAQ5RJV2OtXGQKZwlVo3kixRz4eQOrPWpbau6VpboppzCZ4U
m8JLDYzaEXqeQd5XwebSMfS/+mkxGnRAsvNZIjArIdU8SNZUyUheDuKjPbROyquD
RHr4Ro15FW3hLog7rHZAL9bmpZeZfoDDNegeGd0CgYBlMTkutWRf2NvM8K0uxdt9
BqUcI8vSvtu6k2kBCIv4E9lrmymBZuPTQuulSK1G4nWfj8dnqt2Uznp4eizxNShw
TXIKJGZocl1pZ9YEC6wB5f0KCW4eWgL8i5Zg4KBf2Qmg8buvZ+7EC6f0n4y7Z2j5
fa602wfu8Qz4TuZcLW+p5wKBgCLCSJdBTlwMTJUajy7LITQovLe3VN7EsfRQo1ao
j9E47rqLj9nyCX8RDzNj9R4/Pe0m74Wau9lZbYUtANo96mo6RYEr0w19mUQ2nDgT
Mx7f9ecj94CrseU14N4WlX4V8nQ+uqey9mM++TlXdyrEiU7xPQ2DonOi539c5MrY
uGhVAoGADSYen+9Z2Diwp7NLh3RwJgyccodyQiK5zmZ9U1nKgt5Gush5mnVCzS6i
CMBg/5oEw/+R5t0OoVOsI2/S2GA3RNhzGfzLOtiv3pzah9Ma9J6yeH4PwBB8SICl
jlHv/FXpW8SqRseSJlDCQoXTamhROXK8sKQ18Eb6gvKMRMDYERM=
-----END RSA PRIVATE KEY-----
EOF


chmod 600 /home/condor/.ssh/*
chown condor:condor /home/condor/.ssh/*

# Install condor
apt-get update
curl http://geni-images.renci.org/images/cwang/Condor/installation-packages/condor_8.6.12-446077-ubuntu14_amd64.deb > condor_8.6.12-446077-ubuntu14_amd64.deb              
dpkg -i --force-depends condor_8.6.12-446077-ubuntu14_amd64.deb
apt-get -f -y install
dpkg -i --force-depends condor_8.6.12-446077-ubuntu14_amd64.deb

# Install singularity
#singularity dependencies
sudo apt-get update
sudo apt-get -y install libarchive-dev python dh-autoreconf build-essential squashfs-tools

# install the maste branch
git clone https://github.com/singularityware/singularity.git
cd singularity

# ERRRR, their master branch is not consistent with tutorial!
git checkout vault/release-2.5

./autogen.sh
./configure --prefix=/usr/local
make
sudo make install

# Install pegasus
wget -O - http://download.pegasus.isi.edu/pegasus/gpg.txt | apt-key add -
echo 'deb http://download.pegasus.isi.edu/pegasus/ubuntu trusty main' >/etc/apt/sources.list.d/pegasus.list
apt-get update
apt-get -y install pegasus


#iptables -A INPUT -p tcp --dport 9618 -j ACCEPT
iptables -F


curl http://geni-images.renci.org/images/cwang/Condor/scripts/40-main.config -o /etc/condor/config.d/40-main.config

curl http://geni-images.renci.org/images/cwang/Condor/scripts/50-main.config -o /etc/condor/config.d/50-main.config


MASTER_IP=`ifconfig eth0 | grep Mask | tr -s ' ' | cut -d " " -f 3 | cut -d ":" -f 2`

condor_store_cred -f /etc/condor/pool_password -p test

echo $MASTER_IP master >> /etc/hosts

sed -i 's/127.0.1.1/127.0.0.1/gI' /etc/hosts

NODE_COUNT=`cat /tmp/client_ips.txt | tr -d "][," | wc -w`
for i in $(seq 1 $NODE_COUNT); do
   IP=`cat /tmp/client_ips.txt | tr -d "\n][," | cut -d " " -f $i`
   NODE=`cat /tmp/client_names.txt | tr -d "][," | cut -d " " -f $i`
   echo $IP $NODE >> /etc/hosts
done

#echo > $SLAVES_FILE
for i in $(seq 1 $NODE_COUNT); do
   #NODE_NUM=$((i-1))
   #NODE_NAME=worker-${NODE_NUM}
   NODE_NAME=`cat /tmp/client_names.txt | tr -d "][," | cut -d " " -f $i`
   scp -i /home/condor/.ssh/id_rsa -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no /etc/hosts condor@${NODE_NAME}:.
   ssh -i /home/condor/.ssh/id_rsa -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no condor@${NODE_NAME} 'cat hosts | sed -e '1,/ip6-allhosts/d' >> /etc/hosts'
   ssh -i /home/condor/.ssh/id_rsa -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no root@${NODE_NAME} 'service condor restart'

   #echo ${NODE_NAME} >> $SLAVES_FILE
done

cd $CWD
service condor restart
