# syntax=docker/dockerfile:1
# ---- Stage 1: build ----
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B -DskipTests -Djavacpp.platform=linux-x86_64 package

# ---- Stage 2: runtime ----
FROM eclipse-temurin:25-jre-noble
# LƯU Ý: UID/GID đổi từ 1000 -> 1001 so với bản gốc trong yêu cầu nhiệm vụ. Đã build thực tế
# bằng `docker build -f backend/Dockerfile backend` và phát hiện base image
# eclipse-temurin:25-jre-noble (Ubuntu Noble) đã có sẵn user "ubuntu" ở UID/GID 1000 (quy ước
# mới của Ubuntu cloud/container image từ 24.04), làm `useradd -u 1000` lỗi
# "UID 1000 is not unique". Đây là thay đổi bắt buộc để image build được, không phải lỗi code
# - xem báo cáo cuối nhiệm vụ M0.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r -g 1001 catcheck && useradd -r -g catcheck -u 1001 catcheck \
    && mkdir -p /var/lib/catcheck/storage /tmp/javacpp \
    && chown -R catcheck:catcheck /var/lib/catcheck /tmp/javacpp \
    && chmod 700 /var/lib/catcheck/storage
WORKDIR /app
COPY --from=build /workspace/target/catcheck-*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 --enable-native-access=ALL-UNNAMED"
ENV org.bytedeco.javacpp.cachedir=/tmp/javacpp
USER catcheck
EXPOSE 8080 9090
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
