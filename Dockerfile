FROM gradle:6.1-jdk13 as builder

COPY --chown=gradle:gradle . /home/gradle/src

WORKDIR /home/gradle/src

ADD . .

RUN gradle shadowJar

FROM adoptopenjdk/openjdk13-openj9:jdk-13.0.2_8_openj9-0.18.0-debian
#FROM adoptopenjdk/openjdk13:jdk-13.0.2_8-debian

ENV PYTHONUNBUFFERED=1

#RUN apk --no-cache add curl gcc g++
#
## Taken from: https://github.com/Docker-Hub-frolvlad/docker-alpine-python3/blob/master/Dockerfile
#RUN echo "**** install Python ****" && \
#    apk add --no-cache python3-dev&& \
#    if [ ! -e /usr/bin/python ]; then ln -sf python3 /usr/bin/python ; fi && \
#    \
#    echo "**** install pip ****" && \
#    python3 -m ensurepip && \
#    rm -r /usr/lib/python*/ensurepip && \
#    pip3 install --no-cache --upgrade pip setuptools wheel && \
#    if [ ! -e /usr/bin/pip ]; then ln -s pip3 /usr/bin/pip ; fi

#RUN apk --no-cache add py3-numpy

ENV LANG=C.UTF-8 \
    DEBIAN_FRONTEND=noninteractive \
    PIP_NO_CACHE_DIR=true

# make some useful symlinks that are expected to exist
RUN apt-get update && \
	apt-get install --assume-yes --no-install-recommends python3-dev python3-pip ca-certificates libexpat1 libsqlite3-0 libssl1.1 && \
	apt-get purge --assume-yes --auto-remove -o APT::AutoRemove::RecommendsImportant=false && \
	apt-get install --assume-yes gcc curl g++ && \
	rm -rf /var/lib/apt/lists/*

# Install numpy and jep
RUN pip3 install --no-cache --upgrade pip setuptools wheel
RUN pip3 install --no-cache numpy jep

WORKDIR /javaapp/
COPY --from=builder /home/gradle/src/build/libs/nucleus.jar .

ENV PYTHONPATH /usr/local/lib/python3.7/dist-packages

ARG token
# FIXME: use args
CMD ["java", "-jar", "nucleus.jar", "MjY0ODUzMzI0MjIwNzI3Mjk4.XsyKqg.KX1F-3LMSrFc02pLx96ME-9kJmQ"]