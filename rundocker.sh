#!/bin/bash

echo "Removing previous containers"
docker compose down -v

echo "Building .jar"
./mvnw clean package -DskipTests

echo "Building docker image for BaSyxStarterApplication"
docker build -t basyxstarterapp .

# Secrets deployen und ENV-Variablen in DIESEM Prozess setzen
pushd .secret >/dev/null
bash ./deploy_to_vault.sh
# source: Variablen bleiben im aktuellen Shell-Prozess erhalten
source ./source_secrets.sh
popd >/dev/null
echo "Starting test environment"
docker compose up --build