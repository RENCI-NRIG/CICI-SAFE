#!/bin/bash
CWD=`pwd`

cd Riak-Server
mvn  clean package appassembler:assemble

cd $CWD

