# awn-worker：定时任务与 Outbox 分发（无 Web 端口，actuator 暴露 8081）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY modules ./modules
COPY apps/awn-api/pom.xml apps/awn-api/pom.xml
COPY apps/awn-worker/pom.xml apps/awn-worker/pom.xml
RUN mvn -q -B dependency:go-offline -pl apps/awn-worker -am

COPY apps/awn-api/src apps/awn-api/src
COPY apps/awn-worker/src apps/awn-worker/src
RUN mvn -q -B -DskipTests package -pl apps/awn-worker -am

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV JAVA_OPTS="-XX:+UseG1GC -Xmx512m"
COPY --from=build /build/apps/awn-worker/target/awn-worker-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
