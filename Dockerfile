FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
ARG SERVICE_MODULE=cashclose

# Cache dependency downloads separately from source — only invalidated when pom.xml files change
COPY pom.xml .
COPY fnbx-bom/pom.xml fnbx-bom/
COPY libs/pom.xml libs/
COPY libs/fnbx-archtest/pom.xml libs/fnbx-archtest/
COPY libs/fnbx-shared/pom.xml libs/fnbx-shared/
COPY libs/fnbx-mail/pom.xml libs/fnbx-mail/
COPY libs/fnbx-entities-identity/pom.xml libs/fnbx-entities-identity/
COPY libs/fnbx-entities-platform/pom.xml libs/fnbx-entities-platform/
COPY libs/fnbx-entities-files/pom.xml libs/fnbx-entities-files/
COPY libs/fnbx-entities-notify/pom.xml libs/fnbx-entities-notify/
COPY libs/fnbx-entities-integration/pom.xml libs/fnbx-entities-integration/
COPY libs/fnbx-entities-cashclose/pom.xml libs/fnbx-entities-cashclose/
COPY libs/fnbx-entities-workforce/pom.xml libs/fnbx-entities-workforce/
COPY libs/fnbx-entities-inventory/pom.xml libs/fnbx-entities-inventory/
COPY libs/fnbx-entities-hrm/pom.xml libs/fnbx-entities-hrm/
COPY services/api-gateway/pom.xml services/api-gateway/
COPY services/identity/pom.xml services/identity/
COPY services/platform/pom.xml services/platform/
COPY services/files/pom.xml services/files/
COPY services/notify/pom.xml services/notify/
COPY services/integration/pom.xml services/integration/
COPY services/cashclose/pom.xml services/cashclose/
COPY services/workforce/pom.xml services/workforce/
COPY services/inventory/pom.xml services/inventory/
COPY services/hrm/pom.xml services/hrm/
COPY services/reporting/pom.xml services/reporting/
RUN case "$SERVICE_MODULE" in \
      api-gateway|identity|platform|files|notify|integration|cashclose|workforce|inventory|reporting) ;; \
      *) echo "Unsupported SERVICE_MODULE: $SERVICE_MODULE" >&2; exit 2 ;; \
    esac \
    && mvn -B -pl "services/$SERVICE_MODULE" -am -DskipTests dependency:go-offline

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
