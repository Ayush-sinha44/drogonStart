#!/usr/bin/env bash

set -e

echo "Loading environment variables..."

if [ -f .env ]; then
  set -a
  source .env
  set +a
else
  echo ".env file not found!"
  exit 1
fi

echo "Starting PostgreSQL..."
docker compose up -d

echo "Waiting for PostgreSQL to become ready..."

until docker exec drogon-postgres pg_isready -U scaffolder >/dev/null 2>&1; do
  sleep 1
done

echo "PostgreSQL is ready."

echo "Starting Spring Boot..."
exec ./mvnw spring-boot:run
