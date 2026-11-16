# ADR-0007: Flyway for database migrations

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, issue #26

## Context

Each service owns its schema and must be able to create it from an empty database, on every machine, without manual SQL. The task requires models and migrations in storage owned by each service.

## Decision

We will use **Flyway**. Each service runs its own Flyway migrations at startup against its own database. Migrations are numbered SQL files kept in the service (for example `V1__create_accounts.sql`) and are never edited after they have been merged.

## Alternatives considered

- **Liquibase:** equally capable, but uses XML or YAML change sets, which are heavier to learn and read than plain SQL.
- **Letting Hibernate generate the schema:** not repeatable or reviewable, and unsafe for persistent data.

## Consequences

- A new install or a test database reaches the correct schema automatically.
- Schema changes after merge need a new migration, not an edit.
- Each service's database user needs the rights to run its own migrations.
