#!/bin/bash
java -Djava.security.egd=file:/dev/./urandom \
            -Xms1g \
            -Xmx1g \
            -jar /home/app.jar \
            --spring.profiles.active=${PROFILE:-prod} \
            --spring.cloud.nacos.server-addr=${NACOS_SERVER:-127.0.0.1:8848}
