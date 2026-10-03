#!/bin/sh
set -eu

: "${LIQUIBASE_COMMAND_URL:?Set the direct Neon JDBC URL}"
: "${LIQUIBASE_COMMAND_USERNAME:?Set the Neon migration role}"
: "${LIQUIBASE_COMMAND_PASSWORD:?Set the Neon migration password}"

case "$LIQUIBASE_COMMAND_URL" in
  jdbc:postgresql://*) ;;
  *) echo 'LIQUIBASE_COMMAND_URL must be a PostgreSQL JDBC URL' >&2; exit 2 ;;
esac

authority=${LIQUIBASE_COMMAND_URL#jdbc:postgresql://}
authority=${authority%%/*}
case "$authority" in
  *-pooler.*) echo 'Liquibase needs a direct Neon connection, not the pooler' >&2; exit 2 ;;
  *@*) echo 'Keep database credentials in separate Railway variables, not the URL' >&2; exit 2 ;;
esac
case "$LIQUIBASE_COMMAND_URL" in
  *sslmode=require*|*sslmode=verify-ca*|*sslmode=verify-full*) ;;
  *) echo 'The Neon JDBC URL must enable TLS with sslmode' >&2; exit 2 ;;
esac

liquibase validate
liquibase update
liquibase status
