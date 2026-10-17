# syntax=docker/dockerfile:1
# ---- Stage 1: build ----
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline
# Model embedding cục bộ cho trợ lý RAG (~135MB): revision + SHA-256 ghim trong script; layer riêng,
# đặt trước COPY src để không tải lại mỗi lần đổi code.
COPY tools/fetch-embedding-model.sh tools/
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && tools/fetch-embedding-model.sh /workspace/models/multilingual-e5-small
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
    && mkdir -p /var/lib/catcheck/storage /tmp/javacpp /tmp/djl \
    && chown -R catcheck:catcheck /var/lib/catcheck /tmp/javacpp /tmp/djl \
    && chmod 700 /var/lib/catcheck/storage
WORKDIR /app
COPY --from=build /workspace/target/catcheck-*.jar app.jar
COPY --from=build /workspace/models /app/models
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 --enable-native-access=ALL-UNNAMED"
ENV org.bytedeco.javacpp.cachedir=/tmp/javacpp
# User catcheck không có home ghi được: DJL giải nén native tokenizer vào thư mục này.
ENV DJL_CACHE_DIR=/tmp/djl ENGINE_CACHE_DIR=/tmp/djl \
    CATCHECK_AI_EMBEDDING_MODEL_DIR=/app/models/multilingual-e5-small
USER catcheck
EXPOSE 8080 9090
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
