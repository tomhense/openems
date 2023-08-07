FROM alpine:latest AS downloader

RUN apk update && apk upgrade

RUN apk add --no-cache \
    git \
    py3-pip \
    openjdk17-jre-headless \
    nodejs \
    npm

RUN pip install --break-system-packages lastversion

RUN npm install -g @angular/cli

RUN git clone -b main https://github.com/OpenEMS/openems.git /src
COPY src/ /src

#WORKDIR /src
#RUN ./gradlew buildEdge
#RUN ./gradlew buildBackend

WORKDIR /src/ui
RUN npm install
RUN ng build -c "openems,openems-edge-prod,prod"

WORKDIR /ddl
RUN lastversion --assets --filter openems-edge.jar download https://github.com/OpenEMS/openems
RUN lastversion --assets --filter openems-backend.jar download https://github.com/OpenEMS/openems


FROM ghcr.io/linuxserver/baseimage-alpine:edge

RUN apk update && apk upgrade

RUN apk add --no-cache \
    openjdk17-jre-headless \
    nginx \
    openssl 

RUN mkdir -p /app/www

WORKDIR /app
COPY --from=downloader /ddl /app
COPY --from=downloader /src/ui/target /app/www
COPY root/ /
COPY app/ /app
RUN mkdir -p /{config,data,log}


VOLUME /config
VOLUME /data

EXPOSE 80 443 4200 8075-8080 8082 8084 8085
