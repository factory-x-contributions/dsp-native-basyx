#!/bin/bash

echo "Deploying secrets"
pushd .secret >/dev/null
bash ./deploy_to_vault.sh
source ./source_secrets.sh
popd >/dev/null

echo "Removing previous containers"
docker compose down -v

echo "Building .jar"
./mvnw clean package -DskipTests

echo "Building docker image for DSP-Native-BaSyx"
docker build \
  --secret id=github-token,env=GITHUB_TOKEN \
  --secret id=github-actor,env=GITHUB_ACTOR \
  -t dsp-native-basyx:latest .

echo "Starting test environment"
docker compose up --build