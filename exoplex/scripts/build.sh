#!/bin/bash
CWD=`pwd`
mvn  clean package appassembler:assemble -DskipTests
cd $CWD
