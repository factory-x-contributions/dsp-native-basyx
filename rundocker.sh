#!/bin/bash

echo "Removing previous containers"
docker compose down -v

echo "Building .jar"
./mvnw clean package -DskipTests

echo "Building docker image for BaSyxStarterApplication"
docker build -t basyxstarterapp .

echo "Starting test environment"
docker compose up