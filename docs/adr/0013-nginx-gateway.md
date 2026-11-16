# ADR-0013: Nginx as the single gateway

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, issue #22

## Context

The browser needs one entry point that serves the frontend and reaches the services. The brief requires that ordinary users cannot access the BitTorrent client's administration interface, that internal services are not reachable from outside, and that playback supports HTTP Range requests. The cookie-based authentication (ADR-0009) works best when the frontend and the APIs share one origin.

## Decision

We will use **Nginx** as the only publicly exposed component. It serves the built React app and routes API paths (for example `/api/identity`, `/api/library`, `/api/acquisition`) to the matching service over the internal Compose network. Buffering is turned off on the video route so Range streaming works properly. Nginx only routes: each service still verifies identity and permissions itself.

## Alternatives considered

- **Spring Cloud Gateway:** same language as the services and could check the cookie centrally, but it is one more Java application to write and run, and it does not serve the React build, so something else would still be needed.
- **Exposing each service on its own port:** breaks the same-origin setup and enlarges the attack surface.

## Consequences

- One origin for the frontend and APIs, so no cross-origin configuration is needed.
- Only the gateway publishes a port. Services, databases and qBittorrent are not reachable from outside.
- The gateway does not authenticate requests, so services must never assume a request has already been checked.
- Gateway configuration lives in the repository and must be tested for the video route and for blocked internal paths.
