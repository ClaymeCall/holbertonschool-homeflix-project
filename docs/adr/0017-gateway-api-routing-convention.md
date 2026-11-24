# ADR-0017: Gateway routes `/api/<service>/` and strips the prefix

- **Status:** Proposed
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon. Awaiting review by another learner, as required by the ADR process.
- **Related:** issue #22, issue #15, ADR-0013, ADR-0009

## Context

The gateway (ADR-0013) is the only entry point and must route browser requests to three services. The API contracts (issue #15) and the frontend need one stable convention for the public address of every endpoint. Services should not depend on how they are exposed, so they can be tested directly and moved without changing their code.

## Decision

- Public API paths are `/api/<service>/...`, where `<service>` is `identity`, `library` or `acquisition`.
- The gateway **strips the `/api/<service>` prefix** before forwarding. For example, `/api/library/media/1` reaches the library service as `/media/1`. Services therefore serve their endpoints from the root (no context path), and the OpenAPI contracts document the service paths, with the gateway prefix given as the server URL.
- Operational paths (`/api/<service>/actuator/...`) are **not routed**: the gateway answers 404.
- Any other `/api/...` path answers a JSON 404. Everything else is served from the React app, with unknown paths falling back to `index.html`.
- qBittorrent and PostgreSQL have **no route** and are not on the gateway's network.
- Upstream names are resolved at request time, so the gateway starts and serves the app even if a service is down. If a service is down or too slow, the gateway answers **503** with a plain-language JSON message and no technical details.
- The video route (`/api/library/`) turns off proxy buffering so HTTP Range requests are streamed through.
- The gateway only routes. It does not authenticate: each service verifies identity and permissions itself (ADR-0009).

## Alternatives considered

- **Forward the full path and give each service a context path** (`server.servlet.context-path=/api/identity`): services would depend on their public address, and Actuator health would move to a prefixed path that Docker health checks must know.
- **Route by subdomain or by port:** breaks the single-origin setup that the cookie authentication relies on (ADR-0009).

## Consequences

- Endpoints in the OpenAPI contracts have one clear public address, and the frontend calls only `/api/...` on its own origin.
- The shape of the error JSON used by the gateway is provisional. It must be aligned with the common error format defined in issue #16.
- A new service needs a new location block in `gateway/nginx.conf`, plus an entry in this ADR's convention.
- The gateway sends a `Content-Security-Policy` limited to `img-src` (ADR-0018, SEC-12). It is built at start from `POSTER_ALLOWED_HOSTS` by `gateway/docker-entrypoint.d/40-poster-csp.sh` and written to `csp.inc`, which the server block includes. A location block that sets its own `add_header` would drop the server-level headers, so it must repeat them, and `gateway/tests/check-gateway.sh` checks the policy on every kind of answer.
- HTTP Range streaming through the gateway cannot be fully verified until the library service has a streaming endpoint. It must be tested then.
