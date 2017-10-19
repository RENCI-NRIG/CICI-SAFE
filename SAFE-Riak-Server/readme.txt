--------------- SAFE Riak Server -----------------------------

Starts a Riak server on an ExoGENI VM. 

Description:

--Deploys a Riak server in a Docker container as follows:

  $sudo docker pull yaoyj11/riakimg
  $sudo docker run -i -t  -d -p 2122:2122 -p 8098:8098 -p 8087:8087 -h riakserver --name riakserver yaoyj11/riakimg
  Start riak service
  $sudo docker exec -it riakserver sudo riak start
  $sudo docker exec -it riakserver sudo riak-admin bucket-type activate  safesets
  $sudo docker exec -it riakserver sudo riak-admin bucket-type update safesets '{"props":{"allow_mult":false}}'

--The ExoGENI site where the server is created can be configured in the riak.conf file.

--Log file is created at: logs/riak-server.log

*** Temporary note about logging: Correct logging requires the development version of the Ahab library (version 0.1.7-SNAPSHOT) ***
*** For now you will have to check it out of github and build it manually.  Soon it will be updated in nexus                    ***

Instuctions:

#Build the application
cd SAFE-Riak-Server
./scripts/build.sh

#Edit the config file
vim config/riak.conf

#Run the application
./scripts/run -c config/riak.conf





