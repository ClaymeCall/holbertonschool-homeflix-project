# Technology Stack

> **Draft.** Choices are marked **Decided** (required by the brief or confirmed by the team) or **Open**. Each decision should become an ADR in `docs/adr/` (see [#17](../../../../issues/17)).

## Summary

| Area | Choice | Status |
|---|---|---|
| Backend language and framework | Java 21 (LTS) with Spring Boot | Decided |
| Java build tool | Maven | Decided |
| Number of business services | At least 3, each in its own container | Decided (required by the brief) |
| Frontend | React | Decided |
| Packaging and run | Docker, Docker Compose (`docker compose up --build`) | Decided (required by the brief) |
| Database engine | PostgreSQL | Decided |
| Data isolation | One PostgreSQL server, one separate database per service, each with its own user | Decided |
| Migrations | Flyway | Decided |
| Service-to-service communication | Synchronous REST over HTTP (Spring `RestClient`), on the Docker Compose network | Decided |
| Authentication | JWT signed with RS256, stored in an HttpOnly cookie, `SameSite=Strict` | Decided |
| API contracts | Hand-written OpenAPI YAML in `docs/api/`, written before the code | Decided |
| Backend tests | JUnit 5, Spring Boot Test, MockMvc, Testcontainers (PostgreSQL) | Decided |
| CI | GitHub Actions | Decided |
| Gateway / reverse proxy | Single entry point in front of frontend and services | Open |
| Peer-to-peer client | Existing BitTorrent client driven through its API | Open |
| Frontend build tool, routing, data fetching, styling, language | | Open |
| Frontend tests | | Open |
| Supported video format | Suggested: MP4, H.264 video, AAC audio | Open |

## Decided

### Java, Spring Boot and Maven
Java and Spring Boot are required for all business microservices developed by the team. Failing to use them is a major non-compliance with the brief. Java 21 is the current long-term support release, and Maven is the default build tool of Spring Initializr with the widest documentation.

### Microservices
The suggested boundaries from the brief, to be confirmed in the architecture overview:

| Service | Responsibility |
|---|---|
| Identity and profiles | Accounts, authentication, roles, viewing profiles |
| Library and playback | Indexing, catalogue, video access, viewing progress |
| Acquisition | Authorized downloads, status tracking, handoff of completed files |

A gateway, the frontend and the third-party BitTorrent client do not count toward the three-service minimum. Each service owns its data and no service reads another service's tables.

### React frontend
The frontend technology is open in the brief and must be justified.

- Large ecosystem and documentation, which helps a team of three learn quickly.
- Component model suits the repeated catalogue elements (cards, rows, player controls).
- Good support for accessible, responsive interfaces.

### Docker Compose
The frontend, services and dependencies run together. The repository will include a Dockerfile per developed component, `compose.yaml`, `.env.example`, persistent volumes, health checks, and separate mounts for the source library (read-only) and downloads.

### PostgreSQL, with one database per service
PostgreSQL is the engine for all services. A single PostgreSQL container hosts one **separate database per service**, and each service connects with its **own database user**, which has rights only on its own database. A service therefore cannot read another service's tables, even by mistake. This enforces the brief's rule that no service accesses another service's tables directly. The databases and users are created by an initialization script run at the first start of the container.

### Migrations: Flyway
Each service runs Flyway at startup against its own database. Migrations are numbered SQL files in the service (for example `V1__create_accounts.sql`), so each database is versioned and can be rebuilt from empty.

### Service-to-service communication: REST over HTTP
Services call each other through their documented REST APIs, using Spring's `RestClient`, over the Docker Compose network (by service name). No message broker is used for the MVP, to keep the system simple. Timeouts and error handling for these calls are described in the error-handling document (see [#16](../../../../issues/16)).

### Authentication: JWT (RS256) in an HttpOnly cookie
- The identity service signs a short-lived JWT at sign-in, with a private key.
- The other services only hold the public key and verify the signature themselves, so no shared secret is spread across services.
- The token is stored in an **HttpOnly cookie**, not in JavaScript-accessible storage. This limits token theft through XSS, and it also lets the browser's `<video>` element send the credential automatically when streaming, which an `Authorization` header cannot do.
- The cookie uses **`SameSite=Strict`** to mitigate CSRF.

This relies on the frontend and the APIs being served from the same origin, which must be confirmed with the gateway decision. Token lifetime, the handling of disabled accounts, and how the active profile is selected are defined in the identity and permissions document (see [#14](../../../../issues/14)).

### API contracts: hand-written OpenAPI
OpenAPI YAML files live in `docs/api/` and are written before the implementation. They are the reference that the code and the AI agent's consistency review are checked against. They are not generated from the code.

### Backend tests
JUnit 5 and Spring Boot Test for business rules and inter-service communication, MockMvc for endpoint tests, and **Testcontainers** to run a throwaway real PostgreSQL for integration tests, so tests run against the same engine as production. The brief requires important business rules and critical service communication to be tested.

### Continuous integration: GitHub Actions
On every pull request: build and test each service, build the frontend, and validate `compose.yaml`.

## Open questions

- **Gateway or reverse proxy.** Candidates: Nginx (serves the React build and routes to the services) or Spring Cloud Gateway.
- **BitTorrent client**, and which API it offers for start, status and cancel. Candidates: qBittorrent, Transmission.
- **Frontend details:** build tool, routing, data fetching, styling, TypeScript or JavaScript, and test tools.
- **Supported video format.** The brief suggests MP4 with H.264 video and AAC audio.
