# Agent Service 服务 Dockerfile
# 多阶段构建：根目录全量编译 + 单服务提取
# 注意：构建上下文必须是项目根目录（docker build -f lumina-modules/lumina-business-agent/Dockerfile .）

# 阶段1：构建应用（从根目录编译全部模块）
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 先复制所有 pom.xml 利用 Docker 缓存加速依赖下载
COPY pom.xml ./
COPY lumina-common/pom.xml lumina-common/
COPY lumina-framework/pom.xml lumina-framework/
COPY lumina-agent-core/pom.xml lumina-agent-core/
COPY lumina-gateway/pom.xml lumina-gateway/
COPY lumina-modules/lumina-business-base/pom.xml lumina-modules/lumina-business-base/
COPY lumina-modules/lumina-business-agent/pom.xml lumina-modules/lumina-business-agent/
COPY lumina-modules/lumina-business-notification/pom.xml lumina-modules/lumina-business-notification/
RUN mvn dependency:go-offline -B || true

# 复制全部源码并构建指定模块（含依赖模块）
COPY lumina-common lumina-common
COPY lumina-framework lumina-framework
COPY lumina-agent-core lumina-agent-core
COPY lumina-modules/lumina-business-base lumina-modules/lumina-business-base
COPY lumina-modules/lumina-business-agent lumina-modules/lumina-business-agent
COPY lumina-modules/lumina-business-notification lumina-modules/lumina-business-notification
RUN mvn clean package -pl lumina-modules/lumina-business-agent -am -DskipTests -B

# 阶段2：运行应用
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 添加非root用户
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 从构建阶段复制 jar 包
COPY --from=builder /app/lumina-modules/lumina-business-agent/target/*-exec.jar app.jar

# 暴露端口
EXPOSE 8081

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# JVM 参数优化
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:InitialRAMPercentage=50.0 \
               -XX:+UseG1GC \
               -XX:+UnlockExperimentalVMOptions \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
