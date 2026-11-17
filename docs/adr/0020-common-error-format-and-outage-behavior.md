# ADR-0020: Common error format, timeouts and retries for service calls

- **Status:** Proposed
- **Date:** 2026-11-17
- **Deciders:** Clement Callejon. Awaiting review by another learner, as required by the ADR process.
- **Related:** issue #16, issue #36, ADR-0008, ADR-0017, requirements ARC-08, SEC-04, PLY-05, risks R-23 and R-24

## Context

ADR-0008 says services use timeouts and a common error format, defined in issue #16. The gateway already answers errors as `{"error": "...", "message": "..."}` (ADR-0017) and the identity service adds an optional `fields` map for validation errors. Without one rule, each service would invent its own errors, a slow dependency could stall the services that call it (R-24), and technical details could reach the user (SEC-04). The interface is in French, while server messages are written in English.

## Decision

We will use one error body for the gateway and every service: `{"error": "<code>", "message": "<text>", "fields": {...}}`. `error` is a stable snake_case code, `message` is a short plain-language English text, and `fields` appears only for validation errors. A body never contains a stack trace, a class name, a file path, a SQL fragment or an internal host name.

The frontend translates the `error` code into French. The English `message` is the fallback for a code the frontend does not know, and is what logs, tests and the API contracts show.

Inter-service calls use a connect timeout of 2 seconds and a read timeout of 5 seconds. Only idempotent GET requests are retried: once, after about 200 ms, on a connection failure or a 502, 503 or 504 answer. POST, PUT, PATCH and DELETE are never retried.

A timeout, a refused connection or a 5xx answer from a called service is reported to the caller as **503 `service_unavailable`**, with the same plain message every time. The cause is written to the log.

A missing media file is **404 `file_not_found`** and a path outside the authorized folders is **403 `access_denied`**, both with a generic message that contains no path.

The full list of codes, the French texts and the rules are in [docs/architecture/error-model.md](../architecture/error-model.md).

## Alternatives considered

- **RFC 9457 problem+json:** a standard format, but it would mean rewriting the gateway and the identity service for little gain in a small MVP.
- **French messages from the server:** the user would read the API text directly, but three services and the gateway would all have to stay in French, and the API documentation would too.
- **No retries:** simplest, but a short network blip would become a visible error.
- **Separate 502 and 504 codes for dependency failures:** more precise for operators, but the user cannot act on the difference and the frontend would handle more cases.

## Consequences

- One error handler per service (a `@RestControllerAdvice`) and one `RestClient` configuration give the same behavior everywhere. This is implemented in issue #36.
- The worst-case wait for a retried GET is about 14 seconds (two attempts of 2 s plus 5 s, and the pause). Slow dependencies must be watched, and the timeouts can be tuned in configuration.
- The frontend needs a map from error code to French text. A new code needs a new entry there.
- The gateway's provisional 503 JSON (ADR-0017) is now the standard format and no longer provisional.
- Authentication codes (`unauthenticated`, `forbidden`) are defined here but their behavior depends on the identity design (issue #14).
