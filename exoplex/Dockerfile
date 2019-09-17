FROM openjdk:8-jre-alpine
MAINTAINER Komal Thareja<komal.thareja@gmail.com>
ARG JAR_FILE_SERVER
ARG JAR_FILE_SLICE
WORKDIR /code
ADD target/${JAR_FILE_SERVER} /code/SafeSdxServer.jar
ADD target/${JAR_FILE_SLICE} /code/SafeSdxSliceServer.jar
COPY docker-entrypoint.sh /code
EXPOSE 8080
EXPOSE 8888
VOLUME ["/code"]
ENTRYPOINT ["/code/docker-entrypoint.sh"]
