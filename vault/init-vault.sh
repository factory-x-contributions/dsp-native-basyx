# enable job control (bg, fg)
set -m

# start vault
docker-entrypoint.sh server -dev &

echo "Trying to login..."
until vault login vaultsecret0123456789
do
    echo "Waiting for vault startup..."
    sleep 1
done

set -euo pipefail

echo "Adding consumer certificates"
vault kv put secret/consumerdimsecret content=@/vault/secrets/consumerdimsecret
vault kv put secret/providerdimsecret content=@/vault/secrets/providerdimsecret

while true; do
    sleep 1
done

# and get the actual server process back to the foreground
fg %1
