#!/bin/bash
sudo sh -c 'echo "PermitRootLogin yes" >>/etc/ssh/sshd_config'
#sudo yum install -y docker vim mlocate git maven
sudo systemctl start docker

echo Start Plexus Controller in Container
#sudo docker pull yaoyj11/plexus
sudo docker run -i -t -d -p 8080:8080 -p 6633:6633 -p 3000:3000 -h plexus --name plexus yaoyj11/plexus
sudo docker exec -d plexus /bin/bash -c  "cd /root;pkill ryu-manager; ryu-manager ryu/ryu/app/rest_conf_switch.py ryu/ryu/app/rest_qos.py ryu/ryu/app/rest_router_mirror.py ryu/ryu/app/ofctl_rest.py"

echo Start SAFE Server in Container
#sudo docker pull yaoyj11/safeserver-v7
sudo docker run -i -t -d -p 7777:7777 -h safe --name safe yaoyj11/safeserver-v7
sudo docker exec -d safe /bin/bash -c  "cd /root/safe;sed -i 's/RIAKSERVER/192.5.87.143/g' safe-server/src/main/resources/application.conf;./prdn.sh"
           
echo Create ssh key
sudo sh -c ' ssh-keygen -t rsa -b 4096  -P "" -f "/root/.ssh/id_rsa"  -q'
sudo sh -c ' cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys2'
sudo sh -c ' chmod 600 ~/.ssh/authorized_keys2'

echo Boot Script Done!
