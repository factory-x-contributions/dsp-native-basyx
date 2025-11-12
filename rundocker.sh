#!/bin/bash

echo "Deploying secrets"
pushd .secret >/dev/null
bash ./deploy_to_vault.sh
source ./source_secrets.sh
popd >/dev/null

echo "Removing previous containers"
docker compose down -v

echo "Starting test environment"
docker compose up --build