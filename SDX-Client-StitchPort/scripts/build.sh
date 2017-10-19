#!/bin/bash
CWD=`pwd`

cd SDX_StitchPort_Client

mvn  clean package appassembler:assemble

cd $CWD
