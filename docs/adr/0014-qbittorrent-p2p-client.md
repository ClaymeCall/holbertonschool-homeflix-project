# ADR-0014: Headless qBittorrent as the peer-to-peer client

- **Status:** Accepted. The sentence "the container publishes **no port**" is refined by [ADR-0023](0023-qbittorrent-peer-port-and-enforced-settings.md): the web interface and API port stays unpublished, and only the BitTorrent peer port is published.
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, brief section "Safe and authorized acquisition"

## Context

The brief requires a real peer-to-peer download of a small authorized file, and allows integrating an existing BitTorrent client through its API. Building a BitTorrent engine is not required. A client may share downloaded pieces automatically, and ordinary users must not reach its administration interface.

## Decision

We will use **qBittorrent**, running **headless** (a background daemon, for example the `linuxserver/qbittorrent` image), controlled by the acquisition service through its **Web API**: add a download, read its progress, cancel it.

- The acquisition service owns the business rules (administrator-only access, a verified catalogue, the state of each download in its own database). qBittorrent only transfers data.
- qBittorrent's web interface and API share one port, so the container publishes **no port** and sits on the internal Compose network. Only the acquisition service can reach it.
- Its default login is replaced with credentials from environment variables, never committed.
- Completed files go to a dedicated downloads directory, separate from the read-only source library.
- Seeding after completion is limited.

## Alternatives considered

- **Transmission:** lighter, but its RPC API needs a session-ID handshake on every call.
- **aria2:** has no web interface at all, but has fewer examples and less informative download states.

## Consequences

- qBittorrent's states map to the brief's four visible states (queued, downloading, completed, failed), which the acquisition service translates.
- The demonstration needs a small file whose licence allows reproduction and redistribution, with its source and licence evidence kept in the repository.
- Peer-to-peer is a transfer method and grants no permission to download or share a work. Only entries in the verified catalogue can be downloaded.
