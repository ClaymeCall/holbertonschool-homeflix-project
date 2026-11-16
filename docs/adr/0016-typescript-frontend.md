# ADR-0016: TypeScript for the frontend

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon. Thomas Rousseau (frontend lead) was absent when this was decided and has not yet reviewed it.
- **Related:** issue #17, issue #21, ADR-0005

## Context

ADR-0005 chose React with Vite but left the language open: JavaScript or TypeScript. The frontend consumes several documented REST APIs (ADR-0010) and shows data owned by three services, so shapes such as accounts, profiles, media items and download states are shared between the contracts and the interface. The frontend scaffold (issue #21) cannot be created without choosing one.

## Decision

We will write the frontend in **TypeScript**, using the Vite `react-ts` template with strict compiler settings.

## Alternatives considered

- **JavaScript:** less to learn at the start, and nothing to configure. But mistakes in the shape of API data (a renamed field, a missing value) only appear at runtime, in the browser.

## Consequences

- The compiler catches wrong field names, missing values and mismatched props at build time. This fits an API-driven application and the hand-written OpenAPI contracts.
- Types for API data can be derived from, or checked against, the OpenAPI files.
- There is an extra learning curve for anyone new to TypeScript, and the build runs a type check (`tsc -b`) before bundling.
- The decision is open to challenge by Thomas as frontend lead. If he disagrees, it is replaced by a new ADR that supersedes this one, and the scaffold is converted.
