# ADR-0023: Publish only qBittorrent's peer port, and enforce its login and settings at every start

- **Status:** Proposed
- **Date:** 2026-11-18
- **Deciders:** Thomas Rousseau, after the risk review of 2026-11-17 (Clement Callejon) that decided to publish the peer port (R-21) and to take the login from `.env` (R-18). Awaiting review by another learner, as required by the ADR process.
- **Related:** ADR-0014 (refines its "publishes no port" sentence), ADR-0004, ADR-0013, ADR-0017, issue J4 (#77), risks R-16, R-18, R-20, R-21

## Context

ADR-0014 says that the qBittorrent container "publishes no port". The reason is that its web interface and its API share one port, and that port must never be reachable from outside.

A BitTorrent client also listens on a second port, the **peer port**, where other peers connect to it. If that port is not reachable, the client can only connect out. Two peers that can only connect out cannot exchange data, which makes the real transfer required by the brief (ACQ-14) fragile, especially on a school or presentation network (R-21).

Two other points of ADR-0014 were not implemented yet:

- The default login must be replaced by credentials from environment variables (R-18). Since qBittorrent 4.6.1, there is no fixed default password any more, but a random one is printed in the log at each start, and anything changed through the web interface is kept only in the container's volume.
- Seeding after completion must be limited (R-16).

## Decision

We will publish **only the BitTorrent peer port** of qBittorrent, on TCP and UDP, with the same number inside and outside the container (`QBITTORRENT_PEER_PORT`, default 6881), so the port the client announces is the one peers can reach. The web interface and API port (8080) stays **unpublished**, on the private `p2p` network only, reachable only by the acquisition service. The gateway has no route to it and is not on that network.

We will build a small image on top of the pinned linuxserver.io image (`qbittorrent/Dockerfile`), whose start script writes the HomeFlix settings into qBittorrent's configuration file **at every start**, before the client runs:

- the web interface login from `QBITTORRENT_USERNAME` and `QBITTORRENT_PASSWORD`, stored as a PBKDF2 hash, with no login bypass for localhost or any subnet, host header validation limited to `qbittorrent` and `localhost`, and UPnP off;
- seeding stops at a share ratio (`QBITTORRENT_MAX_RATIO`, default 1.0) or after a time (`QBITTORRENT_MAX_SEEDING_MINUTES`, default 60), whichever comes first, and the torrent is stopped, not deleted;
- incomplete files go to a volume that only qBittorrent mounts, and only completed files reach the downloads folder that the library reads.

If the login is missing, Compose refuses to start. If a setting is invalid, the script switches the web interface off and stops the container.

`qbittorrent/check-isolation.sh` checks all of this against a running stack, and CI runs it.

## Alternatives considered

- **No published port at all (ADR-0014 as written):** the client can only connect out. The transfer then depends on finding a peer that accepts incoming connections, which a school network or a home router often prevents.
- **UPnP to open the port on the router:** does not work from inside a Docker bridge network, and opening router ports automatically is not something a home server should do silently.
- **Setting the login through the Web API after the first start:** needs the random password from the log, and the settings would live only in a volume, where an edit in the web interface could silently undo them.
- **Mounting the start script from the repository:** works, but depends on the file permissions of the host. Copying it into a built image does not, and CI builds it like the other images.

## Consequences

- One more published port: `docker compose config` shows ports on the gateway and on qBittorrent's peer port only. The BitTorrent protocol is exposed on that port, which is what any BitTorrent client does. The web interface, the API and their login are not.
- For incoming connections from the Internet, the household forwards the peer port on its router. The configuration documentation says so (`docs/acquisition/torrent-client.md`).
- Settings changed in the qBittorrent web interface are replaced at the next start. They are changed in `.env` instead.
- The image is built from `qbittorrent/`, so updating qBittorrent means changing the pinned tag in `qbittorrent/Dockerfile`.
- The acquisition service gets the same `QBITTORRENT_*` variables and uses them in J5.
