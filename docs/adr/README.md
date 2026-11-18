# Architecture Decision Records (ADR)

An ADR is a short document that records **one** architecture decision: the situation, what was decided, what else was considered, and the consequences. Together they explain *why* HomeFlix is built the way it is.

## The rule

**Every architecture decision requires an ADR.** No architecture decision is final, and no code depending on it should be merged, until its ADR is accepted.

### What counts as an architecture decision

Write an ADR when a decision:

- chooses or replaces a language, framework, library, tool or third-party component that several parts of the project depend on (for example a database, a gateway, a BitTorrent client);
- defines or changes **service boundaries**, **data ownership**, or how services communicate;
- defines or changes **identity, authentication, authorization or other security** behavior;
- defines or changes the **API conventions** or the shape of a contract shared between services or with the frontend;
- changes **deployment, infrastructure or the runtime environment** (Docker, volumes, mounts, networks, ports exposed);
- sets a **cross-cutting convention** (error format, logging, testing strategy, supported formats);
- **reverses or refines** an earlier ADR.

### What does not need an ADR

Bug fixes, local refactors inside one service, minor dependency updates, and wording or formatting changes. If you are unsure, write the ADR: it takes ten minutes.

## How it works

1. **Propose.** Copy [`template.md`](template.md) to `NNNN-short-title.md`, using the next free number, and fill it in. Keep it short: one decision per ADR.
2. **Review.** Open a pull request that contains the ADR (and ideally the code or documents that depend on it). The ADR is **accepted when the PR is approved by at least one other learner and merged**. Talk it through on Discord first if it is a big decision.
3. **Accept.** Set the status to `Accepted` and add the ADR to the index below.
4. **Never rewrite history.** An accepted ADR is not edited, except to fix typos or to mark it superseded. To change a decision, write a **new** ADR that says `Supersedes ADR-NNNN`, and change the old one's status to `Superseded by ADR-MMMM`.

Statuses: `Proposed`, `Accepted`, `Rejected`, `Superseded by ADR-NNNN`.

### Exception: the initial ADRs

ADR-0001 to ADR-0015 record the decisions taken during the Sprint 0 discussion on 2026-11-16. They were **accepted directly by Clement Callejon and Benjamin Bommier, without a pull request review**. Thomas Rousseau was absent and explicitly left the decision to them. They remain open to challenge: anyone can propose a superseding ADR.

The pull request checklist asks whether the change needs an ADR, and reviewers check it (see [CONTRIBUTING.md](../../CONTRIBUTING.md#architecture-decisions)).

## Index

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions, and require an ADR for each | Accepted |
| [0002](0002-java-spring-boot-maven.md) | Java 21, Spring Boot and Maven | Accepted |
| [0003](0003-microservice-boundaries.md) | Three business microservices with fixed responsibilities | Accepted |
| [0004](0004-docker-compose-deployment.md) | Docker Compose as the run and delivery environment | Accepted |
| [0005](0005-react-frontend-and-tooling.md) | React frontend with Vite, React Router, `fetch` and Vitest | Accepted |
| [0006](0006-postgresql-database-per-service.md) | PostgreSQL, one database and one user per service | Accepted |
| [0007](0007-flyway-migrations.md) | Flyway for database migrations | Accepted |
| [0008](0008-rest-inter-service-communication.md) | Synchronous REST between services | Accepted |
| [0009](0009-jwt-rs256-httponly-cookie.md) | JWT (RS256) in an HttpOnly cookie | Accepted |
| [0010](0010-handwritten-openapi-contracts.md) | Hand-written OpenAPI contracts, written first | Accepted |
| [0011](0011-backend-testing-strategy.md) | JUnit 5, Spring Boot Test, MockMvc and Testcontainers | Accepted |
| [0012](0012-github-actions-ci.md) | GitHub Actions for continuous integration | Accepted |
| [0013](0013-nginx-gateway.md) | Nginx as the single gateway | Accepted |
| [0014](0014-qbittorrent-p2p-client.md) | Headless qBittorrent as the peer-to-peer client | Accepted |
| [0015](0015-mp4-h264-aac-video-format.md) | MP4 with H.264 video and AAC audio as the supported format | Accepted |
| [0016](0016-typescript-frontend.md) | TypeScript for the frontend | Accepted |
| [0017](0017-gateway-api-routing-convention.md) | Gateway routes `/api/<service>/` and strips the prefix | Proposed |
| [0018](0018-posters-as-allowlisted-external-urls.md) | Posters are external image URLs from an allowed host list | Proposed |
| [0019](0019-frontend-styling-approach.md) | CSS Modules with design tokens in CSS custom properties | Proposed |
| [0020](0020-common-error-format-and-outage-behavior.md) | Common error format, timeouts and retries for service calls | Proposed |
| [0021](0021-english-only-interface.md) | English-only interface, server messages shown as written (supersedes part of ADR-0020) | Proposed |
| [0022](0022-internal-service-calls.md) | Internal endpoints on `/internal`, blocked at the gateway, with one token per caller | Proposed |

## Decisions still to make

None at the moment.
