FROM openjdk:20

ARG NACOS_SERVER
ARG PROFILE

ENV NACOS_SERVER=${NACOS_SERVER}
ENV PROFILE=${PROFILE}

# 替换系统apt-get默认软件源用来提升依赖下载速度, 如不需要可以去掉
ADD /docker/sources.list /etc/apt/sources.list
# 尝试关闭 HTTP pipelining, 解决部分依赖下载失败问题.  详见 https://mirrors.ustc.edu.cn/help/debian.html
# RUN echo "Acquire::http::Pipeline-Depth \"0\";" > /etc/apt/apt.conf.d/99nopipelining

VOLUME /tmp
RUN chmod 777 /tmp

WORKDIR /home
ADD /target/*.jar /home/app.jar
ADD /docker/entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh
ENTRYPOINT  ["/home/entrypoint.sh"]

