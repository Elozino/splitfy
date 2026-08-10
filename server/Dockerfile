# --- build ------------------------------------------------------------------
FROM gradle:8-jdk17 AS build
WORKDIR /home/gradle/project

# The whole build is copied because settings.gradle.kts declares the client modules
# too; `.dockerignore` keeps the context small. Gradle only configures what
# `:server:buildFatJar` actually needs.
COPY --chown=gradle:gradle . .

RUN gradle --no-daemon :server:buildFatJar -x test

# --- run --------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

# Never run the API as root.
RUN addgroup -S splitfy && adduser -S splitfy -G splitfy

WORKDIR /app
COPY --from=build --chown=splitfy:splitfy /home/gradle/project/server/build/libs/*-all.jar app.jar

USER splitfy
EXPOSE 8080

# Respect the container's memory limit instead of the host's total RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/ready || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
