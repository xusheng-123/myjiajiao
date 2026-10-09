# 润物细无声 · Nurture 后端 — 标准 OCI/Docker 单体镜像
# 规范约束：禁止 K8s/Service Mesh，直接在 Linux 虚拟机中运行本容器
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
# Maven 国内镜像加速（生产 CI 可按需移除）
RUN mkdir -p /root/.m2 && printf '%s\n' \
    '<?xml version="1.0" encoding="UTF-8"?>' \
    '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">' \
    '  <mirrors>' \
    '    <mirror>' \
    '      <id>aliyun</id>' \
    '      <mirrorOf>central</mirrorOf>' \
    '      <url>https://maven.aliyun.com/repository/public</url>' \
    '    </mirror>' \
    '  </mirrors>' \
    '</settings>' > /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
# P0-2：healthcheck 需要 curl（compose 探活 /actuator/health）
RUN apt-get update && apt-get install -y --no-install-recommends curl     && rm -rf /var/lib/apt/lists/*
RUN useradd -r -u 1001 nurture && mkdir -p /app/data/files && chown -R nurture:nurture /app
USER nurture
COPY --from=build /workspace/target/nurture-backend-*.jar app.jar
EXPOSE 8080
# 仅通过 Cloudflare Tunnel 出站反向连接，严禁向公网暴露应用或数据库端口
ENTRYPOINT ["java", "-XX:+UseZGC", "-jar", "app.jar"]
