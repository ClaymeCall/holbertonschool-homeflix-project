# ADR-0005: React frontend with Vite, React Router, `fetch` and Vitest

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, issue #21, brief section "Technologies"

## Context

The brief leaves the frontend technology open but requires it to be justified. The main journey must be available through the interface, which must be responsive, keyboard accessible and have its own visual identity. Thomas leads the frontend design.

## Decision

We will build the frontend with **React**, using:

- **Vite** as build tool and development server;
- **React Router** for routing between pages (home, movie details, player, administration);
- the browser's **`fetch`** API for calling the services, with no extra data-fetching library;
- **Vitest** with **React Testing Library** for component tests.

The language (JavaScript or TypeScript) and the styling approach are not decided yet and will get their own ADRs.

## Alternatives considered

- **Create React App:** deprecated in favor of tools such as Vite.
- **Another framework (Vue, Angular, Svelte):** viable, but React has the largest ecosystem and documentation for a team learning quickly.
- **A data-fetching library:** unnecessary for an application this small.

## Consequences

- The frontend builds to static files that the gateway serves (ADR-0013).
- Vitest and React Testing Library work directly with Vite, so no extra test configuration is needed.
- The frontend must call the API through the gateway on the same origin, which the authentication design relies on (ADR-0009).
