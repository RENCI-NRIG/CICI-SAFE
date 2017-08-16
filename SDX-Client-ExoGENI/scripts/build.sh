#!/bin/bash
CWD=`pwd`

cd SDX_ExoGENI_Client

mvn  clean package appassembler:assemble

cd $CWD
