# 2. T0 stack and single-module modular monolith

Date: 2026-06-12

## Status

Accepted

## Context

T0 sets up the buildable project skeleton. CLAUDE.md defaults to Java 21 and
Spring Boot 3.x and prescribes a modular monolith with `domain`, `application`,
`web`, `api`, `integrations` boundaries. Deviations from the defaults need human
approval.

## Decision

- **Spring Boot 4.1.0** (human-approved deviation from 3.x). Verified available on
  Maven Central; built on Spring Framework 7, Java 21 baseline satisfied.
- **Single Maven module with package separation** rather than multi-module Maven.
  Boundaries are enforced by an ArchUnit test (`ModularityTest`) in CI, not by
  separate POMs. Lower boilerplate for a < 1000-product monolith; can split into
  Maven modules later if a boundary needs hard compile-time enforcement.
- **Base package / groupId:** `hu.deposoft.webshop` / `hu.deposoft` (human choice).
- **`.env` via native Spring config import** (`spring.config.import=optional:file:./.env[.properties]`)
  instead of a dotenv library — avoids a new dependency (which would need approval).
- **Local PostgreSQL via `spring-boot-docker-compose`**; tests use Testcontainers.

## Consequences

ArchUnit becomes a required test: a layering violation fails the build. Moving to
multi-module later is possible but means repackaging. Spring Boot 4 implies
Spring Framework 7 across all later integrations (springdoc, starters) — their
versions must target Boot 4 when introduced (deferred from T0 to avoid a version
mismatch in the skeleton).
