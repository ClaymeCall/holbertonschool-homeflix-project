# Technology Stack

This page is a **summary**. The reasons, alternatives and consequences of each choice are in the [architecture decision records](../adr/README.md). If this page and an ADR disagree, the ADR wins. Any new or changed architecture decision needs an ADR first (see [CONTRIBUTING.md](../../CONTRIBUTING.md#architecture-decisions)).

## Summary

| Area | Choice | ADR |
|---|---|---|
| Backend | Java 21 (LTS), Spring Boot, built with Maven | [0002](../adr/0002-java-spring-boot-maven.md) |
| Services | Three business microservices: identity and profiles, library and playback, acquisition | [0003](../adr/0003-microservice-boundaries.md) |
| Run and delivery | Docker and Docker Compose (`docker compose up --build`) | [0004](../adr/0004-docker-compose-deployment.md) |
| Frontend | React, Vite, React Router, plain `fetch`, Vitest with React Testing Library | [0005](../adr/0005-react-frontend-and-tooling.md) |
| Frontend language | TypeScript | [0016](../adr/0016-typescript-frontend.md) |
| Frontend styling | CSS Modules, design tokens in CSS custom properties, no component library (proposed) | [0019](../adr/0019-frontend-styling-approach.md) |
| Database | PostgreSQL, one database and one user per service | [0006](../adr/0006-postgresql-database-per-service.md) |
| Migrations | Flyway | [0007](../adr/0007-flyway-migrations.md) |
| Service-to-service calls | Synchronous REST over HTTP (`RestClient`) | [0008](../adr/0008-rest-inter-service-communication.md) |
| Error format and outages | Common `{error, message, fields}` body, 2 s / 5 s timeouts, GET-only retry, 503 for outages (proposed) | [0020](../adr/0020-common-error-format-and-outage-behavior.md) |
| Interface language | English only; the server `message` is the text the user reads (proposed) | [0021](../adr/0021-english-only-interface.md) |
| Authentication | JWT signed with RS256, in an HttpOnly `SameSite=Strict` cookie | [0009](../adr/0009-jwt-rs256-httponly-cookie.md) |
| API contracts | Hand-written OpenAPI YAML in `docs/api/`, written first | [0010](../adr/0010-handwritten-openapi-contracts.md) |
| Backend tests | JUnit 5, Spring Boot Test, MockMvc, Testcontainers (PostgreSQL) | [0011](../adr/0011-backend-testing-strategy.md) |
| CI | GitHub Actions | [0012](../adr/0012-github-actions-ci.md) |
| Gateway | Nginx, the only publicly exposed component | [0013](../adr/0013-nginx-gateway.md) |
| API routing | `/api/<service>/...`, prefix stripped by the gateway (proposed) | [0017](../adr/0017-gateway-api-routing-convention.md) |
| Peer-to-peer client | qBittorrent, headless, internal network only, controlled through its Web API | [0014](../adr/0014-qbittorrent-p2p-client.md) |
| Supported video format | MP4, H.264 video, AAC audio, with `faststart` | [0015](../adr/0015-mp4-h264-aac-video-format.md) |

## Services

| Service | Responsibility | Lead |
|---|---|---|
| Identity and profiles | Accounts, authentication, roles, and viewing profiles | Benjamin |
| Library and playback | Indexing, catalogue, video access, and viewing progress | Clement |
| Acquisition | Authorized downloads, status tracking, and handoff of completed files | Thomas |

A gateway, the frontend and the third-party BitTorrent client do not count toward the three-service minimum. Each service owns its data, and no service reads another service's tables.

## Still to decide (each needs an ADR)

None at the moment.
