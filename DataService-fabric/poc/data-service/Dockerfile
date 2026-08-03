# Spring Boot 数据服务 - 多阶段构建
# Stage 1: 构建（用官方 Maven 镜像，避免本地装 Maven）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先拷 pom.xml 利用 Docker 层缓存（依赖未变则跳过下载）
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再拷源码并打包
COPY src ./src
RUN mvn -B -q -DskipTests package

# Stage 2: 运行（JRE 更小）
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/data-service-*.jar /app/app.jar
USER app
EXPOSE 8090
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD wget --quiet --tries=1 --spider http://localhost:8090/actuator/health || exit 1
