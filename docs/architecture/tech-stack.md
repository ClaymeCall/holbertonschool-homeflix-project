# Technology Stack

> **Draft.** Only choices required by the project brief or already decided by the team are marked **Decided**. Everything else is **Proposed** or **Open** and should become an ADR in `docs/adr/` once confirmed (see [#17](../../../../issues/17)).

## Summary

| Area | Choice | Status |
|---|---|---|
| Backend language and framework | Java with Spring Boot | Decided (required by the brief) |
| Number of business services | At least 3, each in its own container | Decided (required by the brief) |
| Frontend | React | Decided |
| Packaging and run | Docker, Docker Compose (`docker compose up --build`) | Decided (required by the brief) |
| Data stores | One database server, one separate schema per service | Proposed |
| Migrations | Flyway | Proposed |
| Peer-to-peer client | Existing BitTorrent client driven through its API | Open |
| Gateway / reverse proxy | Single entry point in front of frontend and services | Open |
| Authentication | Token-based, validated by each service | Open |
| Backend tests | JUnit 5, Spring Boot Test | Proposed |
| Frontend tests | TBD | Open |
| API contracts | OpenAPI | Proposed |
| CI | GitHub Actions | Proposed |

## Decided

### Java and Spring Boot
Required for all business microservices developed by the team. Failing to use it is a major non-compliance with the brief.

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

Still to decide: build tool, routing, state and data fetching, and styling approach.

### Docker Compose
The frontend, services and dependencies run together. The repository will include a Dockerfile per developed component, `compose.yaml`, `.env.example`, persistent volumes, health checks, and separate mounts for the source library (read-only) and downloads.

## Proposed

### Data stores
One database server hosting a separate schema per service, which keeps data ownership clear while keeping the setup light for a laptop or NAS. The specific database engine is **open**; PostgreSQL is the likely candidate.

### Migrations
Flyway per service, so each schema is versioned and reproducible from an empty database.

### Testing
JUnit 5 and Spring Boot Test for business rules and inter-service communication. The brief requires important business rules and critical service communication to be tested.

### API contracts
OpenAPI files in the repository, reviewed alongside the implementation.

### Continuous integration
GitHub Actions to build and test each service, build the frontend, and validate `compose.yaml` on every pull request.

## Open questions

- Which database engine?
- Which BitTorrent client, and which API does it offer for status and cancel?
- Token format for authentication (for example JWT) and where it is validated?
- Which gateway or reverse proxy?
- Frontend test tools and build tool?
- Which supported video format to document for the MVP (the brief suggests MP4 with H.264 video and AAC audio)?

## Supported format (to confirm)

The MVP must document one supported format. Suggested: **MP4, H.264 video, AAC audio**. Universal transcoding is out of scope.
