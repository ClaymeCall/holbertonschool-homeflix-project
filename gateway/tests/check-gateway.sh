#!/usr/bin/env bash
#
# Checks the gateway end to end, with real HTTP (issue I6, PLY-03, OPS-05, R-23, R-25).
#
# It starts the gateway's own Nginx configuration (gateway/nginx.conf) next to a stand-in for the library
# service, on a private Docker network, and checks:
#   - a Range request through the gateway gives 206 with the right headers and exactly the right bytes;
#   - an unsatisfiable range gives 416 through the gateway;
#   - actuator and unknown /api paths answer a JSON 404;
#   - the gateway answers a plain JSON 503 when the library service is down.
#
# The stand-in is a plain Nginx that serves a random file. Nginx implements Range itself, so this tests what the
# GATEWAY does to a ranged response, not the library service. The library service's own Range rules are tested
# in services/library-service (RangeRequestTest, StreamEndpointTests).
#
# Needs: docker, curl, sha256sum. Run from anywhere:   gateway/tests/check-gateway.sh
# Exit status is 0 when every check passes. It leaves nothing behind.

set -u

IMAGE="nginxinc/nginx-unprivileged:1.30.6-alpine3.24"   # the image gateway/Dockerfile uses
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUN_ID="homeflix-gw-test-$$"
WORK="$(mktemp -d)"
FAILED=0
PASSED=0

cleanup() {
  docker rm -f "$RUN_ID-gateway" "$RUN_ID-library" >/dev/null 2>&1
  docker network rm "$RUN_ID" >/dev/null 2>&1
  rm -rf "$WORK"
}
trap cleanup EXIT

pass() { PASSED=$((PASSED + 1)); echo "  ok    $1"; }
fail() { FAILED=$((FAILED + 1)); echo "  FAIL  $1"; [ -n "${2:-}" ] && echo "        $2"; }

# expect "what is checked" "actual" "expected"
expect() {
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1" "expected [$3], got [$2]"; fi
}

# header NAME FILE: the value of a response header (case-insensitive), without the line end.
header() {
  grep -i "^$1:" "$2" | head -1 | sed -E "s/^[^:]*:[[:space:]]*//" | tr -d '\r\n'
}

# status FILE: the status code of the last response in a header dump.
status() {
  grep -E '^HTTP/' "$1" | tail -1 | awk '{print $2}'
}

for tool in docker curl sha256sum; do
  command -v "$tool" >/dev/null || { echo "Missing tool: $tool"; exit 2; }
done

echo "Preparing the test setup"
SIZE=3145728
mkdir -p "$WORK/library/media/test" "$WORK/gateway-html"
head -c "$SIZE" /dev/urandom > "$WORK/library/media/test/stream"
echo "<!doctype html><title>HomeFlix</title>" > "$WORK/gateway-html/index.html"
cat > "$WORK/library.conf" <<'CONF'
# Nginx adds Accept-Ranges to a whole answer but not to a partial one. The library service sends it on both
# (StreamEndpointTests), so the stand-in does too, to check the gateway passes it on.
map $http_range $partial_accept_ranges {
    default "";
    "~^bytes="    bytes;
}
server {
    listen 8082;
    root /usr/share/nginx/html;
    add_header Accept-Ranges $partial_accept_ranges;
    location / { try_files $uri =404; }
}
CONF
chmod -R a+rX "$WORK"

docker network create "$RUN_ID" >/dev/null || exit 2

# The stand-in library service answers to the name the gateway uses.
docker run -d --name "$RUN_ID-library" --network "$RUN_ID" --network-alias library-service \
  -v "$WORK/library.conf:/etc/nginx/conf.d/default.conf:ro" \
  -v "$WORK/library:/usr/share/nginx/html:ro" "$IMAGE" >/dev/null || exit 2

# The real gateway configuration, as the gateway image installs it.
docker run -d --name "$RUN_ID-gateway" --network "$RUN_ID" -p 127.0.0.1::8080 \
  -v "$ROOT/gateway/nginx.conf:/etc/nginx/conf.d/default.conf:ro" \
  -v "$ROOT/gateway/proxy-common.inc:/etc/nginx/conf.d/proxy-common.inc:ro" \
  -v "$WORK/gateway-html:/usr/share/nginx/html:ro" "$IMAGE" >/dev/null || exit 2

PORT="$(docker port "$RUN_ID-gateway" 8080/tcp | head -1 | sed 's/.*://')"
BASE="http://127.0.0.1:$PORT"

for _ in $(seq 1 50); do
  curl -fs "$BASE/healthz" >/dev/null 2>&1 && break
  sleep 0.2
done
curl -fs "$BASE/healthz" >/dev/null 2>&1 || { echo "The gateway did not start"; docker logs "$RUN_ID-gateway" 2>&1 | tail -20; exit 2; }

URL="$BASE/api/library/media/test/stream"
FILE="$WORK/library/media/test/stream"

# slice START LENGTH: those bytes of the original file.
slice() { tail -c +"$(($1 + 1))" "$FILE" | head -c "$2"; }

# get_range RANGE: fetches with a Range header; sets H (header file) and B (body file).
get_range() {
  H="$WORK/h"; B="$WORK/b"
  curl -s -D "$H" -o "$B" -H "Range: $1" "$URL"
}

echo
echo "Range requests through the gateway (PLY-03)"

H="$WORK/h"; B="$WORK/b"
curl -s -D "$H" -o "$B" "$URL"
expect "whole file: status 200" "$(status "$H")" "200"
expect "whole file: Accept-Ranges is bytes" "$(header Accept-Ranges "$H")" "bytes"
expect "whole file: Content-Length is the file size" "$(header Content-Length "$H")" "$SIZE"
expect "whole file: the bytes are the same" "$(sha256sum < "$B" | cut -d' ' -f1)" "$(sha256sum < "$FILE" | cut -d' ' -f1)"

get_range "bytes=0-99"
expect "first 100 bytes: status 206" "$(status "$H")" "206"
expect "first 100 bytes: Content-Range" "$(header Content-Range "$H")" "bytes 0-99/$SIZE"
expect "first 100 bytes: Content-Length" "$(header Content-Length "$H")" "100"
expect "first 100 bytes: Accept-Ranges" "$(header Accept-Ranges "$H")" "bytes"
cmp -s "$B" <(slice 0 100) && pass "first 100 bytes: the bytes are the same" || fail "first 100 bytes: the bytes differ"

get_range "bytes=1048576-2097151"
expect "a slice in the middle: status 206" "$(status "$H")" "206"
expect "a slice in the middle: Content-Range" "$(header Content-Range "$H")" "bytes 1048576-2097151/$SIZE"
cmp -s "$B" <(slice 1048576 1048576) && pass "a slice in the middle: the bytes are the same (seeking works)" || fail "a slice in the middle: the bytes differ"

get_range "bytes=$((SIZE - 1000))-"
expect "open-ended range: status 206" "$(status "$H")" "206"
expect "open-ended range: Content-Range" "$(header Content-Range "$H")" "bytes $((SIZE - 1000))-$((SIZE - 1))/$SIZE"
cmp -s "$B" <(slice $((SIZE - 1000)) 1000) && pass "open-ended range: the bytes are the same" || fail "open-ended range: the bytes differ"

get_range "bytes=-500"
expect "suffix range: status 206" "$(status "$H")" "206"
expect "suffix range: Content-Range" "$(header Content-Range "$H")" "bytes $((SIZE - 500))-$((SIZE - 1))/$SIZE"
cmp -s "$B" <(slice $((SIZE - 500)) 500) && pass "suffix range: the bytes are the same" || fail "suffix range: the bytes differ"

get_range "bytes=$SIZE-"
expect "a start past the end: status 416" "$(status "$H")" "416"
expect "a start past the end: Content-Range gives the size" "$(header Content-Range "$H")" "bytes */$SIZE"

# The gateway must not hold the response back to fill a buffer, and must not rewrite it.
expect "streaming is not buffered on /api/library/" \
  "$(docker exec "$RUN_ID-gateway" nginx -T 2>/dev/null | awk '/location \/api\/library\//{f=1} f&&/proxy_buffering/{print $2; exit}' | tr -d ';')" "off"
expect "the gateway does not compress or chunk a ranged answer" "$(header Transfer-Encoding "$WORK/h")" ""

echo
echo "Paths that must not be reachable or must be a clear 404 (ADR-0013, ADR-0017)"

for path in /api/library/actuator/health /api/identity/actuator/info /api/acquisition/actuator \
            /api/library/actuator/ /api/LIBRARY/ACTUATOR/health \
            /api/library/internal/downloads/x/completed /api/acquisition/internal /api/identity/INTERNAL/profiles/x/data \
            /api/library//internal/downloads/x /api/library/./internal/downloads/x /api/library/media/../internal/downloads/x \
            /api/library/%69nternal/downloads/x; do
  body="$(curl -s --path-as-is -D "$WORK/h" -o - "$BASE$path")"
  expect "$path: status 404" "$(status "$WORK/h")" "404"
  expect "$path: JSON not_found" "$body" '{"error":"not_found","message":"This address does not exist."}'
done

for path in /api /api/ /api/unknown /api/unknown/anything /api/qbittorrent/api/v2/app/version /api/postgres/; do
  body="$(curl -s -D "$WORK/h" -o - "$BASE$path")"
  expect "$path: status 404" "$(status "$WORK/h")" "404"
  expect "$path: JSON not_found" "$body" '{"error":"not_found","message":"This address does not exist."}'
done

expect "the frontend is served at /" "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/")" "200"
expect "a frontend route falls back to the app" "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/some/screen")" "200"

echo
echo "A service that is down (R-23, R-25)"

docker rm -f "$RUN_ID-library" >/dev/null
sleep 1
UNAVAILABLE='{"error":"service_unavailable","message":"This part of HomeFlix is temporarily unavailable. Please try again in a moment."}'

body="$(curl -s -D "$WORK/h" -o - "$URL")"
expect "library down: status 503" "$(status "$WORK/h")" "503"
expect "library down: plain JSON message" "$body" "$UNAVAILABLE"
expect "library down: JSON content type" "$(header Content-Type "$WORK/h" | cut -d';' -f1)" "application/json"

body="$(curl -s -D "$WORK/h" -o - -H 'Range: bytes=0-99' "$URL")"
expect "library down, ranged request: status 503" "$(status "$WORK/h")" "503"
expect "library down, ranged request: plain JSON message" "$body" "$UNAVAILABLE"

body="$(curl -s -D "$WORK/h" -o - "$BASE/api/identity/ping")"
expect "identity not running: status 503" "$(status "$WORK/h")" "503"
expect "identity not running: plain JSON message" "$body" "$UNAVAILABLE"

grep -qiE 'nginx|upstream|connect\(\)|docker|resolv|refused' <<<"$body" \
  && fail "the 503 message shows no technical detail" "it contains: $body" \
  || pass "the 503 message shows no technical detail"

expect "the gateway itself is still healthy" "$(curl -s "$BASE/healthz" | tr -d '\n')" "ok"

echo
echo "$PASSED passed, $FAILED failed"
[ "$FAILED" -eq 0 ]
