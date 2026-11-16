# ADR-0002: Java 21, Spring Boot and Maven

- **Status:** Accepted
- **Date:** 2026-11-16
- **Deciders:** Clement Callejon and Benjamin Bommier (Thomas Rousseau was absent and delegated the decision to them)
- **Related:** issue #17, brief section "Technologies"

## Context

The brief requires all business microservices to use Java and Spring Boot, and treats not doing so as a major non-compliance. We need a Java version and a build tool.

## Decision

We will write every business microservice in **Java 21 (LTS)** with **Spring Boot**, built with **Maven**. Services are created from Spring Initializr.

## Alternatives considered

- **Gradle:** equally capable, but Maven is the Spring Initializr default and has the widest documentation and examples, which helps a team still learning.
- **Another Java version:** 21 is a long-term support release with broad tool and image support.

## Consequences

- One build tool and one language across the three services, and a Maven build per service in CI and in Dockerfiles.
- The Spring Boot version is the current stable one at project start and is not upgraded during the project unless needed.
