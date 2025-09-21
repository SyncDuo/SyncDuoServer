# 第一阶段：构建应用
FROM maven:3.8.6-eclipse-temurin-17 AS builder

# 设置工作目录
RUN mkdir -p /app
WORKDIR /app

# 复制项目文件
COPY pom.xml .
COPY src ./src

# 构建应用（跳过测试以提高构建速度）
RUN mvn clean package -DskipTests

# 确保有 app.jar 文件生成
RUN find /app -name "*.jar"

# 第二阶段：运行应用
FROM eclipse-temurin:17-jdk

RUN apt-get update && apt-get install -y \
    curl \
    bzip2 \
    && rm -rf /var/lib/apt/lists/*

# 安装 Restic
RUN curl -L https://github.com/restic/restic/releases/download/v0.18.0/restic_0.18.0_linux_amd64.bz2 |  \
    bunzip2 > /usr/local/bin/restic && chmod +x /usr/local/bin/restic

# 设置工作目录
RUN mkdir -p /app
WORKDIR /app

# 从构建阶段复制生成的 JAR 文件
COPY --from=builder /app/target/*.jar app.jar

# 创建非特权用户（虽然容器将以特权模式运行，但仍建议使用非root用户运行应用）
RUN groupadd -r syncduo_server && useradd -r -g syncduo_server syncduo_server
USER syncduo_server

# 暴露应用程序端口
EXPOSE 10000

# 设置容器以特权模式运行的入口点
ENTRYPOINT ["java", "-jar", "/app.jar"]