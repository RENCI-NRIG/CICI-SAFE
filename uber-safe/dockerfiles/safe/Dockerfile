from ubuntu:14.04

RUN apt-get update && apt-get install -y build-essential
RUN apt-get install -y gdb
RUN apt-get install -y default-jdk
RUN apt-get install -y git curl make htop vim emacs wget

RUN apt-get install -y libssl1.0.0
RUN apt-get install -y libpam0g-dev
RUN apt-get install -y libssl-dev

COPY start.sh   /root/
COPY test.sh    /root/

# Install sbt
ENV SBT_VERSION 0.13.12
RUN cd ~/ && wget https://dl.bintray.com/sbt/native-packages/sbt/${SBT_VERSION}/sbt-${SBT_VERSION}.tgz
RUN cd ~/ && tar -zxvf sbt-${SBT_VERSION}.tgz

# Download and SAFE
RUN cd ~/ && git clone https://github.com/RENCI-NRIG/SAFE.git

ENTRYPOINT ["/bin/bash", "/root/start.sh"]
#ENTRYPOINT ["/bin/sh", "-c"]
