#!/usr/bin/env bash
# Checks that the torrent client is isolated and configured as HomeFlix requires (J4, ADR-0014, ADR-0023,
# R-16, R-18, R-20, R-21). Used by CI and by hand.
#
#   qbittorrent/check-isolation.sh [env-file]      (default: .env)
#
# Needs the gateway and qBittorrent running, for example:
#   docker compose --env-file .env up -d --build gateway qbittorrent
#
# Prints one line per check and exits with 1 if any check fails. Secrets are never printed.

set -uo pipefail

ENV_FILE="${1:-.env}"
COMPOSE=(docker compose --env-file "$ENV_FILE")
FAILED=0

pass() { echo "PASS  $1"; }
fail() { echo "FAIL  $1"; FAILED=1; }
check() { if eval "$2"; then pass "$1"; else fail "$1"; fi; }

env_value() {
	# Last value of a variable in the env file, without quotes. Never echoed by the checks.
	grep -E "^$1=" "$ENV_FILE" | tail -n 1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//'
}

if [[ ! -f "$ENV_FILE" ]]; then
	echo "Env file not found: $ENV_FILE" >&2
	exit 2
fi

USERNAME="$(env_value QBITTORRENT_USERNAME)"
PASSWORD="$(env_value QBITTORRENT_PASSWORD)"
PEER_PORT="$(env_value QBITTORRENT_PEER_PORT)"; PEER_PORT="${PEER_PORT:-6881}"
GATEWAY_PORT="$(env_value GATEWAY_PORT)"; GATEWAY_PORT="${GATEWAY_PORT:-8080}"
MAX_RATIO="$(env_value QBITTORRENT_MAX_RATIO)"; MAX_RATIO="${MAX_RATIO:-1.0}"
MAX_MINUTES="$(env_value QBITTORRENT_MAX_SEEDING_MINUTES)"; MAX_MINUTES="${MAX_MINUTES:-60}"

CONFIG_JSON="$(mktemp)"
trap 'rm -f "$CONFIG_JSON"' EXIT
"${COMPOSE[@]}" config --format json >"$CONFIG_JSON" || { echo "docker compose config failed" >&2; exit 2; }
PROJECT="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["name"])' "$CONFIG_JSON")"
IMAGE="$("${COMPOSE[@]}" images --format json qbittorrent | python3 -c 'import json,sys; d=json.load(sys.stdin); d=d if isinstance(d,list) else [d]; print(d[0]["Repository"]+":"+d[0]["Tag"])')"

echo "== Compose file"

# Only the gateway and qBittorrent publish ports, and qBittorrent only its peer port, never 8080.
check "only the gateway and qBittorrent publish ports" "python3 - \"\$CONFIG_JSON\" <<'PY'
import json, sys
services = json.load(open(sys.argv[1]))['services']
publishing = sorted(name for name, s in services.items() if s.get('ports'))
sys.exit(0 if publishing == ['gateway', 'qbittorrent'] else 1)
PY"
check "qBittorrent publishes only the peer port $PEER_PORT (TCP and UDP), not the web interface" "python3 - \"\$CONFIG_JSON\" $PEER_PORT <<'PY'
import json, sys
peer = int(sys.argv[2])
ports = json.load(open(sys.argv[1]))['services']['qbittorrent']['ports']
seen = sorted((int(p['target']), int(p['published']), p.get('protocol', 'tcp')) for p in ports)
sys.exit(0 if seen == [(peer, peer, 'tcp'), (peer, peer, 'udp')] else 1)
PY"
check "qBittorrent is only on the private p2p network, the gateway is not on it" "python3 - \"\$CONFIG_JSON\" <<'PY'
import json, sys
s = json.load(open(sys.argv[1]))['services']
ok = list(s['qbittorrent']['networks']) == ['p2p'] and 'p2p' not in s['gateway']['networks'] \
	and 'p2p' in s['acquisition-service']['networks']
sys.exit(0 if ok else 1)
PY"
check "every other container mounts the downloads folder read-only (ACQ-06, R-20)" "python3 - \"\$CONFIG_JSON\" <<'PY'
import json, sys
services = json.load(open(sys.argv[1]))['services']
source = next(v['source'] for v in services['qbittorrent']['volumes'] if v['target'] == '/downloads')
others = [v for name, s in services.items() if name != 'qbittorrent' for v in s.get('volumes', [])
	if v.get('source') == source]
sys.exit(0 if others and all(v.get('read_only') for v in others) else 1)
PY"
check "only qBittorrent mounts the incomplete downloads volume" "python3 - \"\$CONFIG_JSON\" <<'PY'
import json, sys
services = json.load(open(sys.argv[1]))['services']
users = sorted(name for name, s in services.items() for v in s.get('volumes', []) if v.get('target') == '/incomplete'
	or (v.get('source') or '').endswith('qbittorrent-incomplete'))
sys.exit(0 if users == ['qbittorrent'] else 1)
PY"
check "the gateway has no route to qBittorrent (no configuration line names it)" "! grep -hv '^[[:space:]]*#' gateway/nginx.conf gateway/proxy-common.inc | grep -qi 'qbittorrent'"

echo "== From the host"

check "the web interface is not published (no host port for 8080)" "[[ -z \"\$(\"\${COMPOSE[@]}\" port qbittorrent 8080 2>/dev/null)\" ]]"
check "the peer port $PEER_PORT accepts connections from the host" "python3 -c 'import socket,sys; socket.create_connection((\"127.0.0.1\", int(sys.argv[1])), 3).close()' $PEER_PORT"
check "the gateway does not serve qBittorrent's web interface" "! curl -s -m 5 http://127.0.0.1:$GATEWAY_PORT/ http://127.0.0.1:$GATEWAY_PORT/api/qbittorrent/ http://127.0.0.1:$GATEWAY_PORT/api/v2/app/version | grep -qi 'qbittorrent'"
check "the qBittorrent API is not reachable through the gateway" "[[ \"\$(curl -s -m 5 -o /dev/null -w '%{http_code}' -X POST --data 'username=x&password=y' http://127.0.0.1:$GATEWAY_PORT/api/v2/auth/login)\" != 200 ]]"

echo "== From the other containers"

# A throwaway container on a Compose network, using the qBittorrent image only for its curl.
from_network() {
	local network="$1"; shift
	docker run --rm --network "${PROJECT}_${network}" --entrypoint curl "$IMAGE" -s -m 5 "$@"
}
can_log_in() { # username password: succeeds when the session opened by this login can call the API.
	# qBittorrent 5 answers a successful login with 204 and no body, so the check calls the API with the session.
	docker run --rm --network "${PROJECT}_p2p" --entrypoint sh -e U="$1" -e P="$2" "$IMAGE" -c '
		curl -s -m 5 -c /tmp/c --data-urlencode "username=$U" --data-urlencode "password=$P" -o /dev/null http://qbittorrent:8080/api/v2/auth/login
		test "$(curl -s -m 5 -b /tmp/c -o /dev/null -w "%{http_code}" http://qbittorrent:8080/api/v2/app/version)" = 200'
}

check "a container on the backend network (gateway, services) cannot reach qBittorrent" "! from_network backend -o /dev/null http://qbittorrent:8080/"
check "the API refuses a request without a session" "[[ \"\$(from_network p2p -o /dev/null -w '%{http_code}' http://qbittorrent:8080/api/v2/app/version)\" != 200 ]]"
check "the login from the env file opens a session (acquisition service, p2p network)" "can_log_in \"\$USERNAME\" \"\$PASSWORD\""
check "the old default login admin / adminadmin is refused" "! can_log_in admin adminadmin"
check "a wrong password is refused" "! can_log_in \"\$USERNAME\" wrong-password-123"
check "no temporary password was generated (the login was set before the first start)" "! \"\${COMPOSE[@]}\" logs qbittorrent 2>&1 | grep -qi 'temporary password'"
check "a request for another host name is refused (host header validation)" "[[ \"\$(from_network p2p -o /dev/null -w '%{http_code}' -H 'Host: evil.example' http://qbittorrent:8080/)\" != 200 ]]"

echo "== Settings applied by qBittorrent"

PREFERENCES="$(docker run --rm --network "${PROJECT}_p2p" --entrypoint sh -e U="$USERNAME" -e P="$PASSWORD" "$IMAGE" -c '
	curl -s -m 5 -c /tmp/c --data-urlencode "username=$U" --data-urlencode "password=$P" http://qbittorrent:8080/api/v2/auth/login >/dev/null &&
	curl -s -m 5 -b /tmp/c http://qbittorrent:8080/api/v2/app/preferences')"
pref_is() { # description python-expression on the preferences "p"
	if python3 -c "import json,sys; p=json.load(sys.stdin); sys.exit(0 if ($2) else 1)" <<<"$PREFERENCES" 2>/dev/null; then
		pass "$1"
	else
		fail "$1"
	fi
}
pref_is "seeding stops at ratio $MAX_RATIO (R-16)" "p['max_ratio_enabled'] and abs(p['max_ratio'] - float('$MAX_RATIO')) < 1e-6"
pref_is "seeding stops after $MAX_MINUTES minutes (R-16)" "p['max_seeding_time_enabled'] and p['max_seeding_time'] == int('$MAX_MINUTES')"
pref_is "a torrent that reaches a limit is stopped, not removed" "p['max_ratio_act'] == 0"
pref_is "qBittorrent listens on the published peer port $PEER_PORT" "p['listen_port'] == int('$PEER_PORT')"
pref_is "UPnP is off (the port is published by Compose)" "not p['upnp'] and not p['web_ui_upnp']"
pref_is "the login is required from every address, localhost included" "not p['bypass_local_auth'] and not p['bypass_auth_subnet_whitelist_enabled']"
pref_is "completed files go to /downloads, files in progress to /incomplete" "p['save_path'].rstrip('/') == '/downloads' and p['temp_path_enabled'] and p['temp_path'].rstrip('/') == '/incomplete'"

echo "== Downloads folder"

check "qBittorrent can write to the downloads folder" "\"\${COMPOSE[@]}\" exec -T qbittorrent s6-setuidgid abc sh -c 'touch /downloads/.homeflix-write-check && rm /downloads/.homeflix-write-check'"

if [[ $FAILED -eq 0 ]]; then
	echo "All checks passed."
else
	echo "Some checks failed."
fi
exit $FAILED
