# ADR-0012: GitHub Actions for continuous integration

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, issue #4

## Context

The brief requires Git, a shared repository, tickets, branches and code reviews, and the task asks for continuous integration. The repository is hosted on GitHub.

## Decision

We will use **GitHub Actions**. On every pull request it will build and test each service, build and test the frontend, and validate `compose.yaml`.

## Alternatives considered

- **An external CI service (Jenkins, GitLab CI, CircleCI):** more setup for no benefit, since the code is already on GitHub.
- **No CI:** a broken build could reach `main` unnoticed.

## Consequences

- Checks appear on pull requests and can be made required in branch protection.
- Backend integration tests need Docker in the CI runner for Testcontainers (ADR-0011).
- Workflow files are part of the repository and reviewed like code.
