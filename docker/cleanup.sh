#!/bin/sh
docker stop sdxserver
docker rm sdxserver
docker rmi rencinrig/sdxserver:0.1-SNAPSHOT
docker volume rm $(docker volume ls -qf dangling=true)
