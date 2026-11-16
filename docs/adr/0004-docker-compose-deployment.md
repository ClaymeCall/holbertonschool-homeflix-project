# ADR-0004: Docker Compose as the run and delivery environment

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, brief section "Delivery constraints"

## Context

Another team must be able to clone the repository and start HomeFlix with one documented command. The brief requires Dockerfiles for developed components, `compose.yaml`, a secret-free `.env.example`, persistent volumes, separate mounts for the source library and downloads, and health checks.

## Decision

We will run the frontend, gateway, business services, databases and the BitTorrent client with Docker Compose, started with `docker compose up --build`. Configuration comes from environment variables, with a secret-free `.env.example` committed and real `.env` files ignored by Git. The source media library is mounted read-only, the downloads directory is a separate mount, and each service has a health check.

## Alternatives considered

- **Kubernetes:** far more setup than the MVP needs, and not what the brief asks for.
- **Running services directly on the host:** not reproducible for an outside user.

## Consequences

- Only the gateway publishes a port to the host (see ADR-0013). Everything else stays on the internal Compose network.
- Persistent data (databases, qBittorrent state) uses named volumes, and the backup and restore guide must cover them.
- Docker and Compose become prerequisites in the README.
