# syntax=docker/dockerfile:1

# --- Build stage: compile the Spring Boot bootJar with the Gradle wrapper ---
# Context for this image is apps/backend (a standalone Gradle project).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Resolve dependencies first so this layer is cached unless the build scripts change.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies --quiet || true

# Compile and package. Tests run on the host / CI, never during the image build.
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# --- Runtime stage: slim JRE, non-root, just the runnable jar ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN groupadd --system rota && useradd --system --gid rota --home /app rota
COPY --from=build /app/build/libs/*.jar app.jar
USER rota
EXPOSE 8080
# MaxRAMPercentage lets the JVM size its heap from the container's memory limit.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
