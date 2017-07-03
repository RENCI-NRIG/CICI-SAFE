#!/bin/bash

function run_one_safe_bench() {
  param=$1
  output=$2

  echo "./safebench -d 100s -c ${param} -t ${param} -s input.lua http://152.3.141.201:7777/spki > run_all_data/tmp.txt"
  echo "output: ${output}"

  ./safebench -d 100s -c ${param} -t ${param} -s input.lua http://152.3.141.201:7777/spki > run_all_data/tmp.txt

  throughput=`cat run_all_data/tmp.txt | sed 's/Requests\/sec:    //' | sed -n '7p'`
  percentile1=`cat run_all_data/tmp.txt | sed 's/            1%    //' | sed 's/ms//' | sed -n 10p`
  percentile10=`cat run_all_data/tmp.txt | sed 's/           10%    //' | sed 's/ms//' | sed -n 11p`
  percentile25=`cat run_all_data/tmp.txt | sed 's/           25%    //' | sed 's/ms//' | sed -n 12p`
  percentile50=`cat run_all_data/tmp.txt | sed 's/           50%    //' | sed 's/ms//' | sed -n 13p`
  percentile75=`cat run_all_data/tmp.txt | sed 's/           75%    //' | sed 's/ms//' | sed -n 14p`
  percentile90=`cat run_all_data/tmp.txt | sed 's/           90%    //' | sed 's/ms//' | sed -n 15p`
  percentile95=`cat run_all_data/tmp.txt | sed 's/           95%    //' | sed 's/ms//' | sed -n 16p`
  percentile99=`cat run_all_data/tmp.txt | sed 's/           99%    //' | sed 's/ms//' | sed -n 17p`
  percentile9999=`cat run_all_data/tmp.txt | sed 's/        99.99%    //' | sed 's/ms//' | sed -n 18p`

  echo "${param}        ${throughput}         ${percentile1}       ${percentile10}        ${percentile25}         ${percentile50}        ${percentile75}         ${percentile90}        ${percentile95}         ${percentile99}         ${percentile9999}"  >> run_all_data/${output}
}

num_conns=1
output=$1

while [ $num_conns  -le 25 ]
do
  run_one_safe_bench $num_conns $output
  num_conns=`echo "scala=8; $num_conns + 1" | bc`
done
