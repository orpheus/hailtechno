FROM openjdk:8u181-alpine3.8

WORKDIR /

COPY target/uberjar/hailtechno-0.1.0-standalone.jar hailtechno.jar
EXPOSE 3000

ENV DB_HOST=host.docker.internal
ENV DB_NAME=hailtechno

CMD java -jar hailtechno.jar