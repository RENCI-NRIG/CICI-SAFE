#!/bin/bash

#./target/appassembler/bin/SafeSdxExample ~/.ssl/geni-yuanjuny.pem ~/.ssl/geni-yuanjuny.pem "https://geni.renci.org:11443/orca/xmlrpc" postbootfour fournodes
#./target/appassembler/bin/SafeSdxServer ~/.ssl/geni-yuanjuny.pem ~/.ssl/geni-yuanjuny.pem "https://geni.renci.org:11443/orca/xmlrpc" $1 $2 $3 $4 $5 $6 $7
#./target/appassembler/bin/SafeSdxClient ~/.ssl/geni-yuanjuny.pem ~/.ssl/geni-yuanjuny.pem "https://geni.renci.org:11443/orca/xmlrpc" $1 $2 $3 $4 $5 $6 $7
./target/appassembler/bin/SafeSdxClient $1 $2

