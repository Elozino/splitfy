# syntax=docker/dockerfile:1.7
#
# Production image for the API. Built from the repository root:
#
#   docker build -f backend/Dockerfile -t splitfy-api .
#
# The release workflow (.github/workflows/release-backend.yml) builds this on
# every `v*` tag and pushes it to GHCR.

# --- build ------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# The whole build is copied because settings.gradle.kts declares the client modules
# too; `.dockerignore` keeps the context small. Gradle only configures what
# `:backend:buildFatJar` actually needs, and no Android SDK is required for it.
COPY . .

# The wrapper, not a `gradle:` base image: the build pins its own Gradle version
# (gradle/wrapper/gradle-wrapper.properties) and AGP refuses older ones.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --no-configuration-cache :backend:buildFatJar -x test

# --- run --------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

ARG APP_VERSION=dev
LABEL org.opencontainers.image.title="splitfy-api" \
      org.opencontainers.image.source="https://github.com/Elozino/splitfy" \
      org.opencontainers.image.version="${APP_VERSION}"

# Never run the API as root. A fixed numeric id lets platforms that enforce
# `runAsNonRoot` verify it without reading /etc/passwd.
RUN addgroup -S -g 10001 splitfy && adduser -S -u 10001 -G splitfy splitfy

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/backend/build/libs/*-all.jar app.jar

USER 10001:10001
EXPOSE 8080

# - MaxRAMPercentage: size the heap from the container's memory limit, leaving
#   room for metaspace, thread stacks and Netty's direct buffers.
# - ExitOnOutOfMemoryError: crash and let the platform restart a clean process
#   rather than limp on in an undefined state.
# The JVM reads JAVA_TOOL_OPTIONS on its own, so platforms can append flags
# without rewriting the entrypoint.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8" \
    APP_ENV=PRODUCTION \
    APP_VERSION=${APP_VERSION} \
    LOG_FORMAT=json \
    PORT=8080

# Liveness, not readiness: a database outage should take the instance out of the
# load balancer (via /ready), not get the container killed and restarted.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:${PORT}/health" > /dev/null || exit 1

# Exec form, so the JVM is PID 1 and receives SIGTERM directly. Ktor's shutdown
# hook then flips /ready to 503 and drains in-flight requests
# (ktor.deployment.shutdownGracePeriod / shutdownTimeout in application.yaml).
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
