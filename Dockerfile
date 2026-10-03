FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
ARG SERVICE_MODULE=cashclose
COPY . .
RUN case "$SERVICE_MODULE" in \
      api-gateway|identity|platform|files|notify|integration|cashclose|workforce|inventory|reporting) ;; \
      *) echo "Unsupported SERVICE_MODULE: $SERVICE_MODULE" >&2; exit 2 ;; \
    esac \
    && mvn -B -pl "services/$SERVICE_MODULE" -am -DskipTests package \
    && cp "services/$SERVICE_MODULE/target/$SERVICE_MODULE-1.0.0-SNAPSHOT.jar" /tmp/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /tmp/app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
