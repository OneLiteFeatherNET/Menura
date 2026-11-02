#!/bin/sh
# One-shot generator of the development secrets for the local Garage node, run by the
# garage-secrets service of docker-compose.yml. It writes each secret into the
# garage-secrets volume once: a file that already holds a value is kept, so re-running
# it changes nothing. The values are random per stack and are never committed.
#
#   rpc_secret            64 hex characters (32 bytes), the cluster RPC secret
#   admin_token           64 hex characters, the bearer token of the admin API
#   s3_access_key_id      "GK" + 24 hex characters, the development S3 key ID
#   s3_secret_access_key  64 hex characters, the development S3 secret
#
# The files are mode 600 and owned by root. Delete the volume (docker compose down -v) to
# generate new values.
set -eu

DIR="${SECRETS_DIR:-/run/secrets/garage}"
mkdir -p "$DIR"

# random_hex BYTES prints 2*BYTES lowercase hex characters from the kernel random source.
random_hex() {
  od -An -N "$1" -tx1 /dev/urandom | tr -d ' \n'
}

# create NAME VALUE writes the value once; an existing non-empty file is kept. Every file
# is set to mode 600 because Garage refuses to start with a world-readable secret. Garage
# and garage-init both run as root, so they can still read it.
create() {
  name="$1"
  value="$2"
  if [ -s "$DIR/$name" ]; then
    chmod 600 "$DIR/$name"
    echo "kept $name"
    return 0
  fi
  umask 077
  printf '%s' "$value" >"$DIR/$name.tmp"
  mv "$DIR/$name.tmp" "$DIR/$name"
  echo "created $name"
}

create rpc_secret "$(random_hex 32)"
create admin_token "$(random_hex 32)"
create s3_access_key_id "GK$(random_hex 12)"
create s3_secret_access_key "$(random_hex 32)"
