#!/bin/sh
# Generates a self-signed TLS cert/key for the nginx service's local HTTPS
# listener (:8443 in docker-compose.yml). Run this once before the first
# `docker compose up` - nginx's HTTPS server block (see infra/nginx/nginx.conf)
# won't start without these two files. Safe to re-run; it won't overwrite an
# existing cert (delete infra/nginx/tls/*.pem first if you want a fresh one).
#
# For a real deployment, skip this and put a real certificate (e.g. from
# Let's Encrypt via certbot) at the same two paths instead - see DEPLOYMENT.md.
set -e

# Git Bash (MSYS) rewrites a leading "/CN=..." argument into a Windows path
# before openssl ever sees it - this opts out of that rewrite. A no-op on
# real Linux/macOS shells, where this variable means nothing.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

cd "$(dirname "$0")"

if [ -f tls/fullchain.pem ]; then
  echo "tls/fullchain.pem already exists - not overwriting. Delete it first for a fresh cert."
  exit 0
fi

mkdir -p tls
openssl req -x509 -nodes -days 825 -newkey rsa:2048 \
  -keyout tls/privkey.pem \
  -out tls/fullchain.pem \
  -subj "/CN=localhost"

echo "Generated infra/nginx/tls/fullchain.pem and privkey.pem"
