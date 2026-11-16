# ADR-0010: Hand-written OpenAPI contracts, written first

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, issue #15, [the AI agent persona](../team/ai-agent-persona.md)

## Context

Without shared contracts, interfaces between the services and the frontend may diverge. The brief asks for documented APIs and states that a bounded AI agent should review consistency between documented contracts, OpenAPI definitions and implemented behavior.

## Decision

API contracts are **OpenAPI YAML files written by hand**, kept in `docs/api/`, and written **before** the implementation. Each priority endpoint specifies inputs, outputs, required permissions and errors. The contracts are the reference that the code is checked against.

## Alternatives considered

- **Generating the spec from the code (code-first):** less writing, but then the spec can only describe what the code already does, so it cannot reveal a mismatch, and the AI agent's consistency review would be meaningless.

## Consequences

- The three leads and the frontend can work in parallel against the same contract.
- A contract change is a visible change to a file, reviewed in a pull request, and may need an ADR if it alters a convention shared by services.
- The team must keep the YAML and the code in sync, which the consistency review helps to check.
