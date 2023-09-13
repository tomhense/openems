ARG JAVA_VERSION=openjdk17
ARG SOURCE=local
ARG BRANCH=main
ARG UI_MODE=prod
ARG UI_VERSION=edge


### Build git source base
FROM alpine:latest AS source

ARG BRANCH

RUN apk update && apk upgrade

RUN apk add --no-cache \
    git

RUN git clone -b $BRANCH https://github.com/OpenEMS/openems.git /git
COPY ./ /local


### Build jar builder
FROM alpine:latest AS jar_builder

ARG JAVA_VERSION

RUN apk update && apk upgrade

RUN apk add --no-cache \
    ${JAVA_VERSION}-jdk


### Build ui builder
FROM alpine:latest AS ui_builder

RUN apk update && apk upgrade

RUN apk add --no-cache \
    npm \
    nodejs

RUN npm install -g @angular/cli


### Build edge binary
FROM jar_builder AS build_edge

ARG SOURCE
ARG UI_VERSION=edge

COPY --from=source /$SOURCE /src

WORKDIR /src
RUN ./gradlew buildEdge


### Build backend binary
FROM jar_builder AS build_backend

ARG SOURCE
ARG UI_VERSION=backend

COPY --from=source /$SOURCE /src

WORKDIR /src
RUN ./gradlew buildBackend


### Build ui files
FROM ui_builder AS build_ui

ARG SOURCE
ARG UI_MODE
ARG UI_VERSION

COPY --from=source /$SOURCE /src
COPY docker/ui/src /src

WORKDIR /src/ui
RUN npm install
RUN ng build -c "openems,openems-$UI_VERSION-$UI_MODE,$UI_MODE"


### Build edge exporter
FROM scratch AS edge_export
COPY --from=build_edge /src/build/ /


### Build backend exporter
FROM scratch AS backend_export
COPY --from=build_backend /src/build/ /


### Build ui exporter
FROM scratch AS ui_export
COPY --from=build_ui /src/ui/target/ /ui


### Build jar container base
FROM ghcr.io/linuxserver/baseimage-alpine:edge AS base_container

ARG JAVA_VERSION

RUN apk update && apk upgrade

RUN apk add --no-cache \
    ${JAVA_VERSION}-jre-headless


### Build edge container
FROM base_container AS edge_container

COPY --from=build_edge /src/build /app
COPY docker/edge/root/ /

RUN mkdir -p /config /data

VOLUME /config
VOLUME /data

EXPOSE 8080 8084 8085 502


### Build backend container
FROM base_container AS backend_container

COPY --from=build_backend /src/build /app
COPY docker/backend/root/ /

RUN mkdir -p /config /data

VOLUME /config
VOLUME /data

EXPOSE 8079 8081


### Build ui container
FROM ghcr.io/linuxserver/baseimage-alpine:edge AS ui_container

RUN apk update && apk upgrade

RUN apk add --no-cache \
    nginx \
    openssl

COPY --from=build_ui /src/ui/target /app/www
COPY docker/ui/root/ /

RUN mkdir -p /config /log


VOLUME /config
VOLUME /log

EXPOSE 80 443
