# Lumina Standalone 单体模式 Dockerfile
# 多阶段构建：根目录全量编译 + standalone 提取
# 注意：构建上下文必须是项目根目录（docker build -f lumina-standalone/Dockerfile .），
# 根 pom 的 <modules> 含 lumina-gateway，reactor 解析需要全部模块目录在场。

# 阶段1：构建应用
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY pom.xml ./
COPY lumina-common lumina-common
COPY lumina-framework lumina-framework
COPY lumina-agent-core lumina-agent-core
COPY lumina-gateway lumina-gateway
COPY lumina-modules lumina-modules
COPY lumina-standalone lumina-standalone
RUN mvn -pl lumina-standalone -am package -DskipTests -B

# 阶段2：运行
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /build/lumina-standalone/target/lumina-standalone-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               -XX:+UnlockExperimentalVMOptions \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
