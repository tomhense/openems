ARG BRANCH_VERSION=main
ARG BUILD_VERSION="openems,openems-edge-prod,prod"

FROM alpine:latest AS builder

ARG BRANCH_VERSION
ARG BUILD_VERSION

RUN apk update && apk upgrade

RUN apk add --no-cache \
    git \
    nodejs \
    npm

RUN npm install -g @angular/cli

RUN git clone -b $BRANCH_VERSION https://github.com/OpenEMS/openems.git /src
COPY src/ /src/
RUN patch /src/ui/src/themes/openems/environments/edge-prod.ts /src/ui/src/themes/openems/environments/edge-prod.patch

RUN npm install
RUN ng build -c $BUILD_VERSION


FROM scratch AS export-stage
COPY --from=builder /src/ui/target ./build


FROM ghcr.io/linuxserver/baseimage-alpine:edge

RUN apk update && apk upgrade

RUN apk add --no-cache \
    nginx \
    openssl

COPY --from=builder /src/ui/target /app/www
COPY root/ /

RUN mkdir -p /{config,log}


VOLUME /config
VOLUME /log

EXPOSE 80 443