# AWN Worker 镜像（定时任务与 Outbox 分发；actuator 暴露 8081，无业务端口）
# 构建阶段与 api.Dockerfile 同策略：JDK/Maven 在构建阶段，CENTRAL_MIRROR 可指向镜像仓。
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

ARG MAVEN_VERSION=3.9.9
ARG CENTRAL_MIRROR=https://repo.maven.apache.org/maven2

RUN wget -q -O /tmp/maven.tgz \
      "${CENTRAL_MIRROR}/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
    && mkdir -p /opt/maven && tar -xzf /tmp/maven.tgz -C /opt/maven --strip-components=1 \
    && rm -f /tmp/maven.tgz && ln -s /opt/maven/bin/mvn /usr/local/bin/mvn \
    && printf '<settings><mirrors><mirror><id>central</id><url>%s</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>\n' \
       "${CENTRAL_MIRROR}" > /settings.xml

COPY pom.xml ./
COPY modules ./modules
COPY apps/awn-api/pom.xml apps/awn-api/pom.xml
COPY apps/awn-worker/pom.xml apps/awn-worker/pom.xml
RUN mvn -q -B -ntp -s /settings.xml dependency:go-offline -pl apps/awn-worker -am

COPY apps/awn-api/src apps/awn-api/src
COPY apps/awn-worker/src apps/awn-worker/src
RUN mvn -q -B -ntp -s /settings.xml -DskipTests package -pl apps/awn-worker -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV JAVA_OPTS="-XX:+UseG1GC -Xmx512m"
RUN addgroup -g 10001 -S awn && adduser -u 10001 -S -G awn -h /app awn \
    && ln -sfv /bin/busybox /usr/local/bin/wget
COPY --from=build --chown=awn:awn /build/apps/awn-worker/target/awn-worker-*.jar app.jar
USER awn
# worker 无 HTTP 端口（web-application-type=none，actuator 不绑定），
# 健康口径 = java 进程存活；进程退出时容器随 ENTRYPOINT 退出并由 restart 策略拉起
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
  CMD ps | grep -q "[j]ava" || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
