# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the service with Maven ----
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build

# Download dependencies first so this layer is cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run in CI, not during the image build.
RUN mvn -B -q -DskipTests package \
 && cp target/identity-service-*.jar /build/app.jar

# ---- Runtime stage: small JRE image, non-root user ----
FROM eclipse-temurin:21.0.13_9-jre-alpine-3.24
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER app

EXPOSE 8081

# Liveness check on the Actuator health endpoint (busybox wget is built in).
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8081/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
