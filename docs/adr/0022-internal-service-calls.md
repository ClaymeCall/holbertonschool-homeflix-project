# ADR-0022: Internal service calls on `/internal` paths with one token per caller

- **Status:** Proposed
- **Date:** 2026-11-18
- **Deciders:** Clement Callejon. Awaiting review by another learner, as required by the ADR process.
- **Related:** issue #13, ADR-0008, ADR-0009, ADR-0013, ADR-0017, risk R-12, [consistency rules](../architecture/consistency-rules.md)

## Context

Some operations are made by one service on another, not by a user: the identity service asks the library to delete a profile's data, and the acquisition service tells the library about a download. No user token is involved, so ADR-0009 does not cover them. The gateway forwards every path under `/api/<service>/` to the service (ADR-0017), so a path meant only for other services would be reachable from the browser unless it is blocked.

## Decision

- Calls between services use **internal endpoints** under `/internal/...`. The gateway answers 404 for any `/api/<service>/internal/...` path, so the browser can never reach them.
- A service call carries a secret in the `X-Service-Token` header. The receiving service holds **one secret per caller** and allows each caller only its own endpoints. A caller holds only its own secret.
- Secrets are random values from `.env`, never committed (SEC-05). They are compared in constant time. A service refuses to start without them.
- An internal endpoint never trusts a user token or a user-supplied profile or account id for authorization. It acts on what the caller states, and the caller is trusted for that endpoint only.
- Calls use the timeouts and the error format of ADR-0020.

## Alternatives considered

- **One secret shared by all services:** simpler, but a compromise of the acquisition service, which is also on the peer-to-peer network, would allow deleting any profile's data.
- **Tokens signed by the identity service:** reuses the key pair, but every call would first need the identity service, so the acquisition handoff would fail whenever identity is down.
- **Mutual TLS:** the strongest, but certificates for every service are too much to set up and document in the time available.
- **No authentication, only the private Docker network:** the endpoints would be open to any container that is compromised, and the rule would rest on one layer only.

## Consequences

- Each service needs a small filter for `X-Service-Token`, and `.env` holds one secret per caller. The documentation of the configuration (L4) must list them.
- A leaked secret allows only the endpoints of its caller, not forging user tokens (R-12).
- Rotating a secret means restarting two services. This is acceptable for a home installation.
- The gateway needs one rule that blocks `/internal`, and a test that proves it.
- The secret is not tied to a request, so it does not protect against a caller that is itself compromised. Network isolation and the small set of endpoints limit the effect.
