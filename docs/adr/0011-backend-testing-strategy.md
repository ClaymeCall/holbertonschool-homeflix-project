# ADR-0011: JUnit 5, Spring Boot Test, MockMvc and Testcontainers

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, issue #28, brief section "Reproducible and explainable delivery"

## Context

The brief requires important business rules and critical inter-service communication to be tested, and the tests must be rerunnable from the documentation. Many rules depend on the database (no duplicate catalogue entries, profile isolation, persistence after restart).

## Decision

Backend tests use **JUnit 5** and **Spring Boot Test**, with **MockMvc** for endpoint tests, and **Testcontainers** to start a throwaway real **PostgreSQL** for integration tests.

## Alternatives considered

- **An in-memory database such as H2:** faster to start, but behaves differently from PostgreSQL and can hide errors.
- **Mocking the database layer entirely:** does not prove the migrations or queries work.

## Consequences

- Integration tests run against the same engine as production and also exercise the Flyway migrations.
- Docker must be available wherever the tests run, including in CI (GitHub Actions supports it, see ADR-0012).
- Integration tests are slower than unit tests, so business rules should also have fast unit tests.
