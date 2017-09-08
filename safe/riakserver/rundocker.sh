#!/bin/sh
sudo docker run -i -t  -d -p 2122:2122 -p 8098:8098 -p 8087:8087 -h riakserver --name riakserver riakimg
sudo docker exec -it riakserver sudo riak start
sudo docker exec -it riakserver sudo riak-admin bucket-type activate  safesets
sudo docker exec -it riakserver sudo riak-admin bucket-type update safesets '{"props":{"allow_mult":false}}'

