# Torrent client: isolation and settings

How the qBittorrent container is configured and kept out of reach (issue J4, ADR-0014, [ADR-0023](../adr/0023-qbittorrent-peer-port-and-enforced-settings.md); ACQ-06, ACQ-08 to ACQ-10; risks R-16, R-18, R-20, R-21).

qBittorrent only transfers data. The acquisition service decides what may be downloaded and who may start it (verified catalogue, administrator only), and drives qBittorrent through its Web API.

## Who can reach what

| Port | Published on the host | Reachable from |
|---|---|---|
| 8080, web interface and API | **No** | The acquisition service only, over the private `p2p` network. The gateway has no route to it and is not on that network. |
| `QBITTORRENT_PEER_PORT` (default 6881), BitTorrent peers, TCP and UDP | **Yes**, same number inside and outside | Other BitTorrent peers. The web interface is not on this port. |

The peer port is published so that other peers can connect to HomeFlix, not only the other way round. Without it, a transfer between two peers that both refuse incoming connections cannot happen (R-21).

**For incoming connections from the Internet**, forward the peer port (TCP and UDP) on your router to the computer that runs HomeFlix. UPnP is off on purpose: it cannot open the router's port from inside a Docker network.

## Settings applied at every start

The image is built from `qbittorrent/`: the pinned linuxserver.io image plus a start script (`qbittorrent/configure_qbittorrent.py`), which writes these settings into qBittorrent's configuration file before the client starts. **A change made in the qBittorrent web interface is replaced at the next start**: change `.env` instead.

| Setting | Value | Why |
|---|---|---|
| Web interface login | `QBITTORRENT_USERNAME` and `QBITTORRENT_PASSWORD` from `.env`, stored as a PBKDF2 hash | No default or temporary password (R-18). The acquisition service reads the same variables. |
| Login bypass for localhost or a subnet | Off | Every request needs the login, even from inside the container. |
| Accepted host names | `qbittorrent` and `localhost` | Requests addressed to any other name are refused (host header validation). |
| Seeding limit | Ratio `QBITTORRENT_MAX_RATIO` (default 1.0) or `QBITTORRENT_MAX_SEEDING_MINUTES` (default 60), whichever comes first. The torrent is then **stopped**, not deleted. | Limits redistribution after the download (R-16). A ratio of 1.0 means as much uploaded as downloaded. |
| Listening port | `QBITTORRENT_PEER_PORT` | The port announced to peers is the published one. |
| UPnP and NAT-PMP | Off | See above. |
| Files in progress | `/incomplete`, a volume only qBittorrent mounts, with the `.!qB` extension | The library never sees a partial file (R-19). |
| Completed files | `/downloads`, the `DOWNLOADS_PATH` folder | Mounted read-only by every other container. Only qBittorrent writes there (ACQ-06, R-20). |

If `QBITTORRENT_USERNAME` or `QBITTORRENT_PASSWORD` is empty, `docker compose` refuses to start. If the password is shorter than 12 characters or a number is invalid, the start script switches the web interface off and stops the container, with the reason in `docker compose logs qbittorrent`.

## Configure

In `.env` (see `.env.example`):

```bash
QBITTORRENT_USERNAME=homeflix
QBITTORRENT_PASSWORD=          # at least 12 characters: tr -dc a-f0-9 < /dev/urandom | head -c 32; echo
QBITTORRENT_PEER_PORT=6881
QBITTORRENT_MAX_RATIO=1.0
QBITTORRENT_MAX_SEEDING_MINUTES=60
```

To change the password, edit `.env` and recreate the container: `docker compose up -d qbittorrent`. The old password stops working.

## Check

With the gateway and qBittorrent running:

```bash
docker compose up -d --build --wait gateway qbittorrent
qbittorrent/check-isolation.sh .env
```

The script prints one line per check and fails if any of them fails. It checks the Compose file (published ports, networks, read-only mounts, no gateway route), what the host sees (no web port, peer port open, nothing of qBittorrent through the gateway), what the other containers see (backend network cannot reach it, the old `admin` / `adminadmin` login, a wrong password and a request without a session are refused, the `.env` login works, a foreign host name is refused) and the settings qBittorrent reports through its API. CI runs it on every pull request.

Last run by hand on 2026-11-18 (macOS, Docker Desktop): all 25 checks passed. Also checked by hand: a missing password stops `docker compose`, a 5-character password leaves the web interface off ("The WebUI is disabled"), the password hash is kept across restarts, and after a password change in `.env` the new password works and the old one is refused.
