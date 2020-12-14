FROM openjdk:8-alpine

COPY ./build/libs /opt/inpertio/lib/

EXPOSE 8080/tcp

WORKDIR /opt/inpertio

ENV LOG_LEVEL_DEFAULT=INFO
ENV LOG_LEVEL_APP=INFO
ENV JVM_OPTIONS ""

CMD ["/bin/sh", "-c", "java $JVM_OPTIONS -jar /opt/inpertio/lib/inpertio-server.jar"]