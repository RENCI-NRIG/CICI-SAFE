#!/bin/bash
CWD=`pwd`

cd SAFE_SDX

mvn  clean package appassembler:assemble -DskipTests

cd $CWD
