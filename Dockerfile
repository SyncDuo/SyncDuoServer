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

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

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

# 动态用户创建
ARG USER_ID=1000
ARG GROUP_ID=1000
ARG USERNAME=syncduo-server

# 创建用户和组的健壮方法
RUN set -eux; \
    # 检查组是否存在，不存在则创建 \
    if getent group $GROUP_ID > /dev/null; then \
        existing_group=$(getent group $GROUP_ID | cut -d: -f1); \
        echo "Group with GID $GROUP_ID already exists: $existing_group"; \
        group_name=$existing_group; \
    else \
        groupadd -g $GROUP_ID appgroup; \
        group_name=appgroup; \
    fi; \
    \
    # 检查用户是否存在，不存在则创建 \
    if id -u $USER_ID > /dev/null 2>&1; then \
        existing_user=$(id -un $USER_ID); \
        echo "User with UID $USER_ID already exists: $existing_user"; \
        user_name=$existing_user; \
    else \
        useradd -u $USER_ID -g $GROUP_ID -m $USERNAME; \
        user_name=$USERNAME; \
    fi; \
    \
    # 设置工作目录和日志目录 \
    mkdir -p /app /app/logs; \
    chown -R $USER_ID:$GROUP_ID /app; \
    echo "Using user: $user_name (UID: $USER_ID) and group: $group_name (GID: $GROUP_ID)"

WORKDIR /app

# 从构建阶段复制生成的 JAR 文件
COPY --from=builder --chown=$USER_ID:$GROUP_ID /app/target/*.jar /app/app.jar

# 确保有 app.jar 文件复制成功
RUN find /app -name "*.jar"

# 暴露应用程序端口
EXPOSE 10000
EXPOSE 5572

# 设置容器运行的入口点
ENTRYPOINT ["java", "-jar", "/app/app.jar"]