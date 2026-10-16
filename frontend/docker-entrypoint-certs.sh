#!/bin/sh
# Generates a self-signed certificate on first start when none is mounted.
# In production, mount a real certificate (e.g. Let's Encrypt) into /etc/nginx/certs.
set -e
mkdir -p /etc/nginx/certs
if [ ! -f /etc/nginx/certs/crm.crt ]; then
  echo "No certificate found: generating a self-signed one"
  openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
    -keyout /etc/nginx/certs/crm.key -out /etc/nginx/certs/crm.crt \
    -subj "/CN=${CERT_CN:-localhost}" >/dev/null 2>&1
fi
