# AWN API 镜像（多阶段：JDK/Maven 构建 → JRE 运行；运行镜像不含源码与构建工具）
# 构建阶段基于 eclipse-temurin alpine，Maven 发行版从 Maven 坐标固定版本获取，
# 避免依赖 Docker Hub 的 maven 标签；构建依赖全部经 CENTRAL_MIRROR 指定的源下载
#（默认官方 repo.maven.apache.org，出口网络受限的环境可指向镜像仓，坐标与校验不变）。
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

ARG MAVEN_VERSION=3.9.9
ARG CENTRAL_MIRROR=https://repo.maven.apache.org/maven2

# 固定版本 Maven（busybox wget；镜像只改下载地址，不改构件坐标）
RUN wget -q -O /tmp/maven.tgz \
      "${CENTRAL_MIRROR}/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    && mkdir -p /opt/maven && tar -xzf /tmp/maven.tgz -C /opt/maven --strip-components=1 \
    && rm -f /tmp/maven.tgz && ln -s /opt/maven/bin/mvn /usr/local/bin/mvn \
    && printf '<settings><mirrors><mirror><id>central</id><url>%s</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>\n' \
       "${CENTRAL_MIRROR}" > /settings.xml

# 先复制依赖描述利用镜像层缓存（父 pom 声明了全部模块，两个应用的 pom 都要在场）
COPY pom.xml ./
COPY modules ./modules
COPY apps/awn-api/pom.xml apps/awn-api/pom.xml
COPY apps/awn-worker/pom.xml apps/awn-worker/pom.xml
RUN mvn -q -B -ntp -s /settings.xml dependency:go-offline -pl apps/awn-api -am

COPY apps/awn-api/src apps/awn-api/src
RUN mvn -q -B -ntp -s /settings.xml -DskipTests package -pl apps/awn-api -am

# 运行阶段：精简 JRE（alpine），非 root，镜像自带健康检查
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV JAVA_OPTS="-XX:+UseG1GC -Xmx1g"
# 非 root 运行（固定 uid/gid 10001）；busybox 的 wget 挂到 PATH 供 HEALTHCHECK 使用
RUN addgroup -g 10001 -S awn && adduser -u 10001 -S -G awn -h /app awn \
    && ln -sfv /bin/busybox /usr/local/bin/wget
COPY --from=build --chown=awn:awn /build/apps/awn-api/target/awn-api-*.jar app.jar
USER awn
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health >/dev/null 2>&1 || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
