FROM liquibase/liquibase:4.29.2

WORKDIR /liquibase/db
COPY changelog/ /liquibase/db/changelog/
COPY railway-migrate.sh /liquibase/db/railway-migrate.sh

ENV LIQUIBASE_SEARCH_PATH=/liquibase/db \
    LIQUIBASE_COMMAND_CHANGELOG_FILE=changelog/db.changelog-master.xml \
    LIQUIBASE_LIQUIBASE_SCHEMA_NAME=public \
    LIQUIBASE_SHOW_BANNER=false

ENTRYPOINT ["/bin/sh", "/liquibase/db/railway-migrate.sh"]
