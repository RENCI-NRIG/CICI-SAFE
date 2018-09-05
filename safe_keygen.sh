#!/bin/bash

main() {
  if [ $# -ne 3 ]; then
    usage
    exit 1
  fi

  if [ ! -e $3 ]; then
    mkdir $3
  else 
    if [ ! -d $3 ]; then
      echo "$3 already exists but is not a directory"
      exit 1
    fi
  fi

  echo "Generating $2 keys with key filename prefix $1 under $3"
  generate_keys $1 $2 $3
}

usage() {
  echo "Usage: $0 <key-filename-prefix> <number-of-keys> <directory>"
}

inc() {
  return `echo "scale=8; $1 + 1" | bc`
}

generate_keys() { 
  key_name_prefix=$1
  num_keys=$2
  key_dir=$3

  count=1
  while [ $count -le $num_keys ]
  do
    ssh-keygen -t rsa -b 4095  -P "" -f "${key_dir}/${key_name_prefix}${count}"  -q
    inc $count
    count=$?
    printf "."
  done
  printf "\nDone\n"
}

main "$@"
