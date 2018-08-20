#!/bin/bash
sudo apt-get install -y maven openjdk-8-jdk
git clone https://github.com/RENCI-NRIG/ahab.git
cd ahab
sudo maven install
cd ..
