# ADR-0009: JWT (RS256) in an HttpOnly cookie

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, issue #14, brief section "Safe, accessible, and respectful behavior"

## Context

Users sign in once, and every service must verify identity and permissions on protected requests without reading the identity service's tables. The browser's `<video>` element cannot send an `Authorization` header, so a token kept in JavaScript would break video streaming. The brief requires secure authentication, protection against XSS and relevant CSRF risks, and server-side authorization on every protected resource.

## Decision

- The identity service signs a short-lived **JWT** at sign-in using **RS256** (a private key).
- Other services hold only the **public key** and verify the signature themselves.
- The token is stored in an **HttpOnly cookie** with **`SameSite=Strict`**, not in JavaScript-accessible storage.
- The frontend and the APIs are served from the **same origin** through the gateway (ADR-0013).

Token lifetime, handling of disabled accounts, role claims and how the active profile is selected are specified in the identity and permissions design (issue #14).

## Alternatives considered

- **Token in `localStorage` or in an `Authorization` header:** readable by JavaScript, so exposed to XSS, and unusable by the `<video>` element.
- **HS256 with a shared secret:** simpler, but every service would hold the signing secret, so a compromise of any service lets an attacker forge tokens.
- **Server-side sessions:** workable, but each service would need to query a shared session store or the identity service on every request.

## Consequences

- Cookies are sent automatically, so streaming works and the token is not reachable from scripts.
- `SameSite=Strict` mitigates CSRF, but depends on the same-origin setup. State-changing endpoints must still be reviewed for CSRF risks.
- A signed token stays valid until it expires, so lifetimes must be short and revocation (for example disabled accounts) must be considered.
- The private key and any secrets must come from the environment, never from the repository. The public key must be distributed to the other services.
