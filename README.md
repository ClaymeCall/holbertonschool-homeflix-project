# HomeFlix

HomeFlix is a self-hosted video streaming platform for the home. It lets a household browse and watch videos stored on a computer, a personal server or a NAS, through a simple, welcoming web interface.

> **Status:** Sprint 0, scope and architecture. The installation guide will be added once the Docker Compose environment is ready.

## The scenario: movie night at home

A household has a collection of videos on a NAS but no convenient way to watch them on the living-room laptop. HomeFlix connects to that library so that:

- an **administrator** creates an account, connects the media folder and starts indexing;
- **household members** sign in, choose their own profile, then find, play and resume a film;
- each profile keeps its **own favourites ("My List") and viewing progress**.

## What the MVP covers

- Account and role management, and viewing profiles
- Indexing of an authorized media folder, with no duplicates and with detection of missing files
- Catalogue navigation: recently added, categories, favourites, continue watching, search by title, filter by genre
- Movie pages with title, description, poster, genre, duration and availability
- Browser playback with seeking (HTTP Range requests) and saved progress per profile
- Authorized peer-to-peer acquisition of redistributable content, with a rights reminder

## What it does not cover (yet)

Universal transcoding, series, recommendations, parental controls, mobile or TV apps, remote Internet access, and support for every NAS or network protocol. See [docs/product/out-of-scope.md](docs/product/out-of-scope.md).

## Architecture in one paragraph

HomeFlix is split into at least three Java/Spring Boot business microservices (identity and profiles, library and playback, acquisition), each running in its own container and owning its own data, behind a React frontend. Everything starts with Docker Compose. Details are in [docs/architecture/tech-stack.md](docs/architecture/tech-stack.md).

## Documentation

| Topic | File |
|---|---|
| Team and working agreement | [docs/team/working-agreement.md](docs/team/working-agreement.md) |
| Mandatory requirements checklist | [docs/product/requirements.md](docs/product/requirements.md) |
| Acceptance criteria for the 10 MVP scenarios | [docs/product/acceptance-criteria.md](docs/product/acceptance-criteria.md) |
| Out of scope and future extensions | [docs/product/out-of-scope.md](docs/product/out-of-scope.md) |
| Architecture overview (diagram, boundaries) | [docs/architecture/overview.md](docs/architecture/overview.md) |
| Technology stack | [docs/architecture/tech-stack.md](docs/architecture/tech-stack.md) |
| Supported video format and conversion | [docs/user/supported-video-format.md](docs/user/supported-video-format.md) |
| Torrent client: isolation and settings | [docs/acquisition/torrent-client.md](docs/acquisition/torrent-client.md) |
| Architecture decision records | [docs/adr/](docs/adr/README.md) |
| Risk register | [docs/risks.md](docs/risks.md) |
| API contracts (OpenAPI) | [docs/api/](docs/api/README.md) |
| Data model (entities per service) | [docs/architecture/data-model.md](docs/architecture/data-model.md) |
| Error model and outage behavior | [docs/architecture/error-model.md](docs/architecture/error-model.md) |
| Gateway checks (Range, blocked paths, 503) | [gateway/tests/README.md](gateway/tests/README.md) |
| User journeys | [docs/product/user-journeys.md](docs/product/user-journeys.md) |
| Prioritized backlog | [docs/product/backlog.md](docs/product/backlog.md) |
| Identity and permissions design | [docs/architecture/identity-and-permissions.md](docs/architecture/identity-and-permissions.md) |
| Cross-service consistency rules | [docs/architecture/consistency-rules.md](docs/architecture/consistency-rules.md) |
| Demo: one service calls another | [docs/architecture/service-to-service-demo.md](docs/architecture/service-to-service-demo.md) |
| Copyright and peer-to-peer awareness | [docs/copyright-and-p2p.md](docs/copyright-and-p2p.md) |
| Contributing (branches, commits, pull requests) | [CONTRIBUTING.md](CONTRIBUTING.md) |

## Rights and peer-to-peer

Peer-to-peer is only a transfer method. It does not grant permission to download or share a work. HomeFlix is only meant to be used with personal videos, your own creations, or content whose licence allows the intended use. Read the [copyright and peer-to-peer awareness note](docs/copyright-and-p2p.md) before the first download.

## Team

Built by [ClaymeCall](https://github.com/ClaymeCall), [Benji3106](https://github.com/Benji3106) and [tomsonne](https://github.com/tomsonne) as a Holberton School project.
