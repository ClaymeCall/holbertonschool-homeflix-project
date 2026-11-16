# ADR-0003: Three business microservices with fixed responsibilities

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, issue #10, brief section "Technologies"

## Context

The brief requires at least three genuine Java/Spring Boot business microservices, each in its own container, each owning its data. A gateway, the frontend or a third-party BitTorrent client do not count. Running copies of one monolith is not acceptable. The brief suggests three boundaries and allows another decomposition if justified.

## Decision

We will use the three suggested services, each with one lead:

| Service | Responsibility | Lead |
|---|---|---|
| Identity and profiles | Accounts, authentication, roles, and viewing profiles | Benjamin |
| Library and playback | Indexing, catalogue, video access, and viewing progress | Clement |
| Acquisition | Authorized downloads, status tracking, and handoff of completed files | Thomas |

Each service owns its data (see ADR-0006), exposes a documented API (ADR-0010), and reaches other services only through those APIs (ADR-0008).

## Alternatives considered

- **A different decomposition** (for example splitting indexing and playback): possible, but more services than a two-week, three-person team can build and keep consistent.
- **A single backend:** not allowed by the brief.

## Consequences

- Each learner can own one service end to end, which also makes it easier to explain individual contributions.
- Cross-service rules (for example what happens to progress when an account is deleted) must be designed explicitly (issue #13).
- The detailed diagram and boundary rationale are in the architecture overview (issue #10).
