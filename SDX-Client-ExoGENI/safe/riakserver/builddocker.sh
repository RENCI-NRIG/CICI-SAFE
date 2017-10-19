#!/bin/bash

sudo docker build -t riakimg .

#simple docker command:
#build a docker image with Dockerfile under current directory, the image name is "test":

#docker build -rm -t test .

#check the status of docker images

sudo docker images

## run a docker as name "test1" at port 2122 by using image "test"
#
#docker run -i -t  -d -p 2122:2122 -p 8098:8098 -h test1 --name test1 test
#
##check the status of the docker
#
#docker ps -a

