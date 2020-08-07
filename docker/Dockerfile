from ubuntu:19.10

RUN apt-get update && apt-get install -y build-essential
RUN apt-get install -y gdb openjdk-8-jdk screen python git curl make htop maven
RUN apt-get remove -y openjdk-11-jre-headless

# Download Orca
RUN cd ~ && git clone https://github.com/RENCI-NRIG/orca5.git
RUN cd ~/orca5 &&  mvn clean install

# Download ahab
RUN cd ~ && git clone https://github.com/RENCI-NRIG/ahab.git
RUN cd ~/ahab && sed -i "s/5\.4\.13/5.4.14/g" pom.xml 
RUN cd ~/ahab && mvn install

# Download CICI-SAFE
RUN cd ~/ && git clone https://github.com/RENCI-NRIG/CICI-SAFE.git

# Compile SAFE
RUN cd ~/CICI-SAFE/exoplex && mvn  clean package appassembler:assemble -DskipTests

