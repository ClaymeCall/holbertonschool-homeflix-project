# ADR-0006: PostgreSQL, one database and one user per service

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, issue #11, issue #12, brief section "Technologies"

## Context

Each service must own its data, and no service may access another service's tables directly. The brief allows separate databases or schemas, hosted on one database server. We need an engine and an isolation model that enforces the rule instead of relying on discipline.

## Decision

We will use **PostgreSQL**, running as one server in one container. It hosts **one separate database per service**, and each service connects with its **own database user**, which has rights only on its own database. The databases and users are created by an initialization script when the container first starts. Credentials come from environment variables.

## Alternatives considered

- **One database with one schema per service:** allowed by the brief and simpler, but a misconfigured account could still read other schemas. Separate databases and users make the boundary physical.
- **One database server per service:** strongest isolation, but too heavy for a laptop or NAS.
- **Another engine (MySQL, H2):** PostgreSQL is widely used with Spring Boot and works well with Flyway and Testcontainers.

## Consequences

- A service cannot read another service's tables, even by mistake, which directly supports the requirement and is easy to demonstrate.
- No foreign keys across services. References such as a profile ID in the library service are plain values, and consistency is handled by explicit rules (issue #13).
- The initialization script and credentials are part of the Compose setup and `.env.example`.
- Backups must cover every database.
