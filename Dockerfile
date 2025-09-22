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

# 更新系统依赖
RUN apt-get update && apt-get install -y \
    curl \
    unzip \
    bzip2 \
    && rm -rf /var/lib/apt/lists/*

# 安装 Rclone
RUN curl -O https://downloads.rclone.org/rclone-current-linux-amd64.zip && \
    unzip rclone-current-linux-amd64.zip && \
    cd rclone-*-linux-amd64 && \
    cp rclone /usr/bin/ && \
    chown root:root /usr/bin/rclone && \
    chmod 755 /usr/bin/rclone

# 安装 Restic
RUN curl -L https://github.com/restic/restic/releases/download/v0.18.0/restic_0.18.0_linux_amd64.bz2 |  \
    bunzip2 > /usr/local/bin/restic && chmod +x /usr/local/bin/restic

# 设置工作目录和日志目录
RUN mkdir -p /app /app/logs
WORKDIR /app

# 从构建阶段复制生成的 JAR 文件
COPY --from=builder /app/target/*.jar /app/app.jar

# 确保有 app.jar 文件复制成功
RUN find /app -name "*.jar"

# 暴露应用程序端口
EXPOSE 10000
EXPOSE 5572

# 设置容器运行的入口点
ENTRYPOINT ["java", "-jar", "/app/app.jar"]