# syntax=docker/dockerfile:1
#
# Comfy Cash Close — backend container image.
#
# Multi-stage: stage 1 builds the multi-module Maven reactor (database-migration
# + comfy-case-close-app), stage 2 ships only the JRE and the fat jar.
#
# Java 21 (LTS), matching <java.version>21</java.version> in the root pom and the
# Corretto 21 JDK used for local development. The image is self-contained — it
# does not use the host's JDK — but keeping the versions aligned means local
# builds and container builds compile against exactly the same language level.
#
# Build locally:   docker build -t comfy-api .
# Run locally:     docker run -p 8080:8080 --env-file .env comfy-api
# On Railway:      the Dockerfile is auto-detected; no further config needed.

# ---------------------------------------------------------------------------
# Stage 1 — build
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the poms alone first. Docker caches this layer, so dependency download is
# only repeated when a pom actually changes — not on every source edit.
COPY pom.xml ./
COPY comfy-case-close-app/pom.xml comfy-case-close-app/
COPY database-migration/pom.xml database-migration/

# Warm the local Maven repository. `|| true` is deliberate: comfy-case-close-app
# declares a dependency on database-migration, which has not been built yet at
# this point, so go-offline always reports it as unresolvable. That failure is
# harmless — the real build below resolves it from the reactor.
RUN mvn -B -q dependency:go-offline || true

# Now the sources.
COPY comfy-case-close-app/src comfy-case-close-app/src
COPY database-migration/src database-migration/src

# Tests are skipped on purpose: ComfyCaseCloseApplicationTests starts the full
# Spring context and needs a reachable Postgres. There is no Testcontainers
# setup yet, so tests cannot run inside an isolated image build. Run them in CI
# or locally against the docker-compose database instead.
RUN mvn -B -DskipTests clean package

# ---------------------------------------------------------------------------
# Stage 2 — runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# The app writes VN-local timestamps (hibernate.jdbc.time_zone=Asia/Ho_Chi_Minh);
# keeping the container clock in the same zone avoids surprises in logs.
ENV TZ=Asia/Ho_Chi_Minh

# Don't run as root.
RUN groupadd --system spring && useradd --system --gid spring spring

# spring-boot-maven-plugin produces exactly one executable jar here; the
# non-executable copy it leaves behind is named *.jar.original and is not matched.
COPY --from=build /build/comfy-case-close-app/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080

# MaxRAMPercentage keeps the heap inside whatever memory limit the platform sets
# (the JVM default of 25% wastes most of a small container).
#
# --server.port=${PORT:-8080}: Railway injects PORT at runtime and expects the
# process to bind it. Falls back to 8080 for local runs, so no extra env var is
# needed in either place.
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -jar app.jar --server.port=${PORT:-8080}"]
