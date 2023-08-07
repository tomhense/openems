ARG GIT_BRANCH=main
ARG JAVA_VERSION=openjdk17


FROM alpine:latest AS builder

ARG GIT_BRANCH
ARG JAVA_VERSION

RUN apk update && apk upgrade

RUN apk add --no-cache \
    git \
    ${JAVA_VERSION}-jre-headless

RUN git clone -b $GIT_BRANCH https://github.com/OpenEMS/openems.git /src

WORKDIR /src
RUN ./gradlew buildBackend


FROM scratch AS export-stage
COPY --from=builder /src/openems-backend.jar ./build


FROM ghcr.io/linuxserver/baseimage-alpine:edge

ARG JAVA_VERSION

RUN apk update && apk upgrade

RUN apk add --no-cache \
    ${JAVA_VERSION}-jre-headless

COPY --from=builder /src/openems-backend.jar /app/openems-backend.jar
COPY root/ /

RUN mkdir -p /{config,data}

VOLUME /config
VOLUME /data

EXPOSE 8079
