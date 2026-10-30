#!/bin/sh
set -eu

: "${STATUS_PAGE_URL:=}"
: "${STATUS_PUBLIC_API_URL:=}"
export STATUS_PAGE_URL STATUS_PUBLIC_API_URL

envsubst '${STATUS_PAGE_URL} ${STATUS_PUBLIC_API_URL}' \
  < /opt/ces/env.template.js \
  > /usr/share/nginx/html/env.js
