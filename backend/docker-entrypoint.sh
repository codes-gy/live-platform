#!/bin/sh
# Heroku Postgres는 DATABASE_URL(postgres://user:pass@host:port/db)만 준다. Spring이 읽는 JDBC 형식으로 바꾼다.
set -e
if [ -n "$DATABASE_URL" ] && [ -z "$DB_URL" ]; then
  rest=${DATABASE_URL#*://}
  creds=${rest%%@*}
  export DB_USERNAME="${creds%%:*}"
  export DB_PASSWORD="${creds#*:}"
  export DB_URL="jdbc:postgresql://${rest#*@}?sslmode=require"
fi
exec java -jar /app/app.jar
