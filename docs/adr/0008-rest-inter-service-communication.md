# ADR-0008: Synchronous REST between services

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, issue #16, issue #27

## Context

Services need to cooperate without sharing tables: for example, the acquisition service hands a completed file over to the library service. The brief requires handling of errors and service outages, and testing of critical inter-service communication.

## Decision

Services will call each other through their documented **REST APIs over HTTP**, using Spring's `RestClient`, on the Docker Compose network (by service name). Calls use timeouts and a common error format, defined in the error-handling document (issue #16).

## Alternatives considered

- **A message broker (RabbitMQ, Kafka):** decouples services better, but adds infrastructure and complexity that a two-week MVP does not justify.
- **Shared database access:** forbidden by the brief and by ADR-0006.

## Consequences

- Simple to build, debug and demonstrate: one request between two services is enough to prove the communication.
- A service depends on the availability of the services it calls, so outages must be handled explicitly and shown to the user in understandable language.
- Contracts between services must be documented (ADR-0010) and covered by integration tests.
