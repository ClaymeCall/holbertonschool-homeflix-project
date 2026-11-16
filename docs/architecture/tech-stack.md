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
| Gateway / reverse proxy | Nginx: single entry point, serves the React build and routes to the services | Decided |
| Peer-to-peer client | qBittorrent, headless, controlled through its Web API | Decided |
| Frontend build tool | Vite | Decided |
| Frontend routing | React Router | Decided |
| Frontend data fetching | Plain `fetch` | Decided |
| Frontend tests | Vitest with React Testing Library | Decided |
| Frontend language | JavaScript or TypeScript | Open (Thomas) |
| Frontend styling | Plain CSS, Tailwind or other | Open (Thomas) |
| Supported video format | MP4 container, H.264 video, AAC audio, with `faststart` | Decided |

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

Supporting choices:

- **Vite** as build tool and development server: the current standard for new React projects, fast to start and simple to configure. It replaces the deprecated Create React App.
- **React Router** for routing between pages such as the home page, a movie page and the player.
- **Plain `fetch`** for calling the APIs, because the application is small and needs no extra data library.
- **Vitest with React Testing Library** for component tests, since both integrate directly with Vite.

The frontend language (JavaScript or TypeScript) and the styling approach are left to Thomas, who leads the frontend design. HomeFlix must have its own visual identity, without copying existing streaming brands.

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

### Gateway: Nginx
Nginx is the single public entry point. It serves the built React app and routes API paths (for example `/api/identity`, `/api/library`, `/api/acquisition`) to the matching service over the internal Docker network. Only the gateway publishes a port to the host. The services, the databases and the BitTorrent client are not reachable from outside. Because the frontend and the APIs share one origin, the `SameSite=Strict` cookie works without cross-origin configuration. Buffering is turned off on the video route so HTTP Range streaming works properly. Nginx only routes: each service still verifies identity and permissions itself.

### Peer-to-peer client: qBittorrent
HomeFlix integrates an existing BitTorrent client, as the brief allows, instead of building one. qBittorrent runs headless (a background daemon, for example the `linuxserver/qbittorrent` image), and the acquisition service controls it through its Web API: add a download, read its progress, cancel it.

- The acquisition service owns the business rules: administrator-only access, a verified catalogue, and the state of each download in its own database. qBittorrent only transfers the data.
- qBittorrent's Web API and its web interface share one port, so the container has **no published port** and sits on the internal Docker network. Only the acquisition service can reach it, which keeps the admin interface away from ordinary users.
- Its default login must be replaced with credentials from environment variables, never committed.
- Completed files go to a dedicated downloads directory, separate from the read-only source library.
- Seeding after completion is limited, and the demo uses only a small file whose licence allows redistribution, with the evidence kept in the repository.

Alternatives considered: Transmission (lighter, but its RPC needs a session-ID handshake on each call) and aria2 (no web interface at all, but fewer examples and less informative statuses).

### API contracts: hand-written OpenAPI
OpenAPI YAML files live in `docs/api/` and are written before the implementation. They are the reference that the code and the AI agent's consistency review are checked against. They are not generated from the code.

### Backend tests
JUnit 5 and Spring Boot Test for business rules and inter-service communication, MockMvc for endpoint tests, and **Testcontainers** to run a throwaway real PostgreSQL for integration tests, so tests run against the same engine as production. The brief requires important business rules and critical service communication to be tested.

### Continuous integration: GitHub Actions
On every pull request: build and test each service, build the frontend, and validate `compose.yaml`.

### Supported video format: MP4, H.264, AAC
The MVP documents one supported format: an **MP4** container with **H.264** video and **AAC** audio. All major browsers play it natively in a `<video>` element, and it works with HTTP Range requests so users can seek without downloading the whole file.

- The MP4 file must have its index at the start (`faststart`), otherwise seeking is slow because the player must read to the end of the file first.
- Other formats (for example MKV, AVI or H.265) are not supported. HomeFlix shows a clear "format not supported" message instead of failing silently.
- Universal transcoding is out of scope.

A compatible test file can be prepared with:

```
ffmpeg -i input.mov -c:v libx264 -c:a aac -movflags +faststart out.mp4
```

## Open questions

- **Frontend language:** JavaScript or TypeScript (Thomas).
- **Frontend styling approach** (Thomas).
