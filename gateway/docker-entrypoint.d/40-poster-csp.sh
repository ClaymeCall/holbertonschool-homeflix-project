#!/bin/sh
# Builds the Content-Security-Policy of the gateway from POSTER_ALLOWED_HOSTS (issue #59, ADR-0018, SEC-12, R-33).
#
# The library service reads the same variable to validate a poster address when it is saved (PosterPolicy), so
# what the server accepts and what the browser may load cannot drift apart. The policy limits img-src to
# HomeFlix itself and to those hosts, https only. An empty list gives img-src 'self'.
#
# Each entry must be a plain host name, written like the library service writes them. Anything else (a
# wildcard, a port, a scheme, a space inside, a quote or a semicolon) stops the gateway from starting, so a bad
# value can never add a source to the header or inject a directive.
set -eu

OUT="${CSP_INCLUDE_FILE:-/etc/nginx/conf.d/csp.inc}"
# Unset gives the default, like the library service; an empty value is an empty list (nothing but HomeFlix).
LIST="${POSTER_ALLOWED_HOSTS-upload.wikimedia.org}"

SOURCES="'self'"
OLD_IFS="$IFS"
IFS=','
for raw in $LIST; do
  IFS="$OLD_IFS"
  # Trim spaces around the entry, lower-case it, skip an empty one (a trailing comma).
  host="$(printf '%s' "$raw" | tr 'A-Z' 'a-z' | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
  IFS=','
  [ -z "$host" ] && continue
  if ! printf '%s' "$host" | grep -Eq '^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$'; then
    echo "40-poster-csp: POSTER_ALLOWED_HOSTS must be a comma-separated list of host names, got: $host" >&2
    exit 1
  fi
  SOURCES="$SOURCES https://$host"
done
IFS="$OLD_IFS"

cat > "$OUT" <<EOF
# Generated at start from POSTER_ALLOWED_HOSTS by 40-poster-csp.sh. Do not edit.
add_header Content-Security-Policy "img-src $SOURCES" always;
EOF
echo "40-poster-csp: img-src $SOURCES"
