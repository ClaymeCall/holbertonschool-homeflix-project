# ADR-0001: Record architecture decisions, and require an ADR for each

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17

## Context

HomeFlix is built by three learners in two weeks. Without a shared record, architecture choices live in conversations, get forgotten, and can be silently contradicted by someone else's code. The brief also asks the team to explain and justify its technical choices at the presentation, and to avoid a distributed monolith.

## Decision

We will record every architecture decision as an Architecture Decision Record in `docs/adr/`, one decision per file, numbered in order. An ADR is **required** for any architecture decision, as defined in [the ADR README](README.md), and an ADR is accepted when its pull request is approved by another learner and merged. Accepted ADRs are not rewritten: a change of mind is a new ADR that supersedes the old one. The initial ADRs (0001 to 0015) were accepted directly by Clement Callejon and Benjamin Bommier, as Thomas Rousseau was absent and delegated the decision to them. Later ADRs follow the pull request process.

## Alternatives considered

- **One large technology document:** easy to start, but it hides history, mixes decisions together, and is rewritten rather than appended, so the reasons for changes are lost.
- **Decisions only in issues or chat:** quick, but not discoverable later and not part of the repository.
- **ADRs only for "big" decisions:** the line is subjective, so the rule is stated broadly and the README lists what counts.

## Consequences

- Each architecture decision costs a few minutes of writing, which is accepted in exchange for traceability and easier presentation.
- Reviewers must check pull requests for missing ADRs. The pull request template and CONTRIBUTING.md include this check.
- `docs/architecture/tech-stack.md` becomes a summary that links to the ADRs, not a source of decisions.
