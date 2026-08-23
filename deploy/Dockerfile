# NL2SQL 应用镜像（多阶段构建）
# 构建：docker build -t nl2sql-app .
# 运行：见 docs/部署手册.md

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先拷贝 pom 拉依赖（利用层缓存）
COPY pom.xml .
RUN mvn -B dependency:go-offline -q || true
COPY src ./src
RUN mvn -B package -DskipTests -q

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/nl2sql-app-*.jar app.jar

# JVM 参数对齐章程 §4.2（2G 内存环境）
ENV JAVA_OPTS="-Xms480m -Xmx960m -XX:MaxMetaspaceSize=320m -XX:+UseG1GC"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
