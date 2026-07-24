# Gateway 服务 Dockerfile
# 多阶段构建：根目录全量编译 + 单服务提取
# 注意：构建上下文必须是项目根目录（docker build -f lumina-gateway/Dockerfile .）

# 阶段1：构建应用
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 复制所有 pom.xml 利用缓存（含聚合器 pom，否则 Maven 找不到子模块报错）
COPY pom.xml ./
COPY lumina-common/pom.xml lumina-common/
COPY lumina-framework/pom.xml lumina-framework/
COPY lumina-agent-core/pom.xml lumina-agent-core/
COPY lumina-gateway/pom.xml lumina-gateway/
COPY lumina-modules/pom.xml lumina-modules/
COPY lumina-modules/lumina-business-base/pom.xml lumina-modules/lumina-business-base/
COPY lumina-modules/lumina-business-agent/pom.xml lumina-modules/lumina-business-agent/
COPY lumina-modules/lumina-business-notification/pom.xml lumina-modules/lumina-business-notification/
COPY lumina-standalone/pom.xml lumina-standalone/
RUN mvn dependency:go-offline -B || true

# 复制源码并构建（只需目标模块及其依赖的源码，但聚合器 pom 目录必须存在）
COPY lumina-common lumina-common
COPY lumina-framework lumina-framework
COPY lumina-agent-core lumina-agent-core
COPY lumina-gateway lumina-gateway
RUN mvn clean package -pl lumina-gateway -am -DskipTests -B

# 阶段2：运行
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/lumina-gateway/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               -XX:+UnlockExperimentalVMOptions \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
