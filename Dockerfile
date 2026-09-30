# syntax=docker/dockerfile:1
#
# Backend image for linux/amd64 and linux/arm64 from ONE Dockerfile:
#   docker buildx build --platform linux/amd64,linux/arm64 -t currency-backend .
#
# Stage 1 runs on the machine's own architecture ($BUILDPLATFORM): a Java jar is the same for every
# CPU, so there is no need to compile under slow QEMU emulation. Only stage 2 is per-platform, and it
# just copies files onto the official multi-arch JRE image.
# Tests are not run here; they run with ./mvnw verify (locally / in CI) before an image is built.

ARG TEMURIN_VERSION=21.0.12.1_1

FROM --platform=$BUILDPLATFORM eclipse-temurin:${TEMURIN_VERSION}-jdk-noble AS build
# mvnw needs 'unzip' to use the .zip Maven distribution whose SHA-256 is pinned in
# .mvn/wrapper/maven-wrapper.properties (without it, it downloads a .tar.gz and the check fails).
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
# Dependencies first: this layer is reused until pom.xml changes. The BuildKit cache mount keeps
# ~/.m2 between builds without putting it into the image.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/app.jar extract --layers --launcher --destination /workspace/extracted

# Ubuntu-based JRE (not Alpine): Kafka Streams' RocksDB native library needs glibc.
FROM eclipse-temurin:${TEMURIN_VERSION}-jre-noble
RUN groupadd --system --gid 1001 app \
 && useradd --system --uid 1001 --gid app --home-dir /app app \
 && mkdir -p /var/lib/currency/streams \
 && chown -R app:app /var/lib/currency
WORKDIR /app
# Spring Boot layers, least to most frequently changing, so small code changes only replace the
# last (small) layer when an image is rebuilt or pulled.
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./
USER app
# Kafka Streams state (RocksDB); mount a volume here to keep it across container restarts.
ENV APP_STREAMS_STATE_DIR=/var/lib/currency/streams
EXPOSE 8080
# Size the heap from the container's memory limit rather than the host's.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
