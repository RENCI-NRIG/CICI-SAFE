#!/bin/bash
CWD=`pwd`

cd SDX_Client

mvn  clean package appassembler:assemble

cd $CWD
