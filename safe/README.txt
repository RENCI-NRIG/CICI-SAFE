1. Deploy a riak server
  ./builddocker.sh
  ./rundocker.sh

2. Run a SAFE server
  (1). Configure SAFE server
    vim safe-server/src/main/resourcse/application.conf
    Set the ip address of storeURI to the IP address of the riak server
  (2). generate keypairs
  (3) start a SAFE server

(3) Use a

