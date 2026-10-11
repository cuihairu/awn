# 阶段 1：Maven 构建（离线优先）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先复制依赖描述，利用镜像层缓存
COPY pom.xml ./
COPY modules ./modules
COPY apps/awn-api/pom.xml apps/awn-api/pom.xml
RUN mvn -q -B dependency:go-offline -pl apps/awn-api -am

# 再复制源码构建
COPY apps/awn-api/src apps/awn-api/src
RUN mvn -q -B -DskipTests package -pl apps/awn-api -am

# 阶段 2：运行
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV JAVA_OPTS="-XX:+UseG1GC -Xmx1g"
COPY --from=build /build/apps/awn-api/target/awn-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
