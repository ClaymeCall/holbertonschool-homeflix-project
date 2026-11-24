# ADR-0024: Show service status by calling one administrator operation of each service

- **Status:** Proposed
- **Date:** 2026-11-21
- **Deciders:** Thomas Rousseau (frontend lead). Awaiting review by another learner, as required by the ADR process.
- **Related:** issue K1 (#84), ADR-0017, ADR-0020, ADR-0021

## Context

The administration area must show the status of the services in plain language (K1). Each service exposes a health endpoint, but only for Docker: the gateway answers 404 to `/api/<service>/actuator` and must keep doing so (ADR-0017). The browser cannot reach a service any other way.

When a service is down or too slow, the gateway answers 503 with a plain message (ADR-0017). When a service cannot reach its database or another service, it answers 503 too (ADR-0020).

## Decision

We will check each service from the Status page of the administration area by calling one read-only administrator operation that its contract already documents:

| Service | Call |
|---|---|
| Identity | `GET /api/identity/accounts?limit=1` |
| Library | `GET /api/library/roots` |
| Acquisition | `GET /api/acquisition/entries` |

Each of these reads the service's database. A successful answer means **Working**. A 5xx answer, or no answer at all, means **Unavailable**. The page then says what stops working, for example "Nobody can sign in". Any other answer, such as 403 after the account lost its administrator role, is shown as **Not checked**, with the message of the answer (ADR-0021).

The three calls run at the same time, when the page opens and when the administrator asks to check again. Nothing is checked in the background.

## Alternatives considered

- **Route a health path through the gateway** (`/api/<service>/health` to the Actuator health endpoint): would include the database check, but it reopens an operational path that ADR-0017 closes on purpose, and it changes the gateway for one screen.
- **Add a `GET /status` operation to each service:** the most complete, since it could also report the torrent client. It means new code and a contract change in the three services for one screen, at the end of the project.

## Consequences

- No backend or gateway change. The status follows the services' own outage behaviour (ADR-0020), so it shows what a member of the household would see.
- The torrent client is not checked. A download in error already gives its reason on the Downloads page (ADR-0021, J6). The Status page says so.
- If one of the three operations is removed or becomes expensive, the Status page must be given another one. The calls are listed in a single place, `frontend/src/api/status.ts`.
- A service that answers but works badly in a way that does not give a 5xx, for example a slow disk, is shown as working.
