# T0 — Project skeleton & CI design

**Date:** 2026-06-12 · **Task:** B-webshop `TASKS.md` T0 · **Status:** approved (brainstorming)

## Context

First task of the B-webshop track (nemiskacat.hu WooCommerce replacement). The full
system design lives in `docs/TERV.md` and the work-package spec in `docs/SPEC.md`; the
binding engineering rules are in the root `CLAUDE.md`. This document scopes only T0: the
buildable project skeleton plus a build+test CI workflow.

Several T0 acceptance items depend on human-provided infrastructure (`[EMBER]`): a staging
environment, a managed DB, and the CI staging-deploy target. Those are deferred and marked
`DECISION NEEDED`; everything locally buildable is delivered now.

## Approved decisions

- **Stack:** Java 21, Spring Boot **4.1.0** (human-approved deviation from the CLAUDE.md
  3.x default; verified available on Maven Central, built on Spring Framework 7), Maven.
- **Module shape:** single Maven module with package separation
  (`domain`, `application`, `web`, `api`, `integrations`, `config`). Boundaries enforced by
  an ArchUnit test rather than separate Maven modules.
- **Base package / groupId:** `hu.deposoft.webshop` / `hu.deposoft`.
- **CI:** GitHub Actions `build.yml` running `mvn verify` on Java 21 for every push/PR.
  The deploy job is a placeholder pending the staging target (`[EMBER]`).

## Project layout

```
CLAUDE.md  README.md  pom.xml  .gitignore  .env.example  compose.yaml
src/main/java/hu/deposoft/webshop/
  WebshopApplication.java
  domain/  application/  web/  api/  integrations/  config/
src/main/resources/
  application.yml  application-local.yml
  db/migration/V1__baseline.sql
  templates/  static/
src/test/java/hu/deposoft/webshop/
  WebshopApplicationTests.java
  architecture/ModularityTest.java
content/blog/   docs/{TERV.md,SPEC.md,adr/,superpowers/}   admin-ui/  (placeholder)
.github/workflows/build.yml
```

## Component decisions

- **Health:** Spring Boot Actuator `/actuator/health` is the real health endpoint. A thin
  `api/` REST controller demonstrates the API layer and is covered by the modularity test.
- **`.env` handling (no new library):** `spring.config.import=optional:file:./.env[.properties]`.
  A `.env` file in `KEY=VALUE` form parses as a properties source. `.env` is gitignored;
  `.env.example` documents the variables. This avoids introducing a dotenv dependency, which
  would need human approval per CLAUDE.md.
- **Local PostgreSQL:** `compose.yaml` + `spring-boot-docker-compose` so `mvn spring-boot:run`
  auto-starts Postgres. Tests use Testcontainers (Postgres).
- **Layer discipline:** ArchUnit test asserts that `web`/`api` controllers stay thin and that
  `domain` does not depend on `web`/`api`/integration code (CLAUDE.md rule #1).
- **Flyway baseline:** `V1__baseline.sql` is a real, minimal first migration (enable `pg_trgm`,
  create an `app_metadata` key/value table) so the baseline is demonstrably applied.

## Out of scope / DECISION NEEDED

- **Staging environment + managed DB** (`[EMBER]`): not provisioned. App is verified locally.
- **CI staging-deploy** (`[EMBER]`): deploy job is a documented placeholder; target, secrets,
  and deploy mechanism are decided once the environment exists (D2).
- Everything in T1+ (Woo export, Kulcs-Soft, catalog, cart, checkout, …).

## Acceptance criteria verifiable now

1. `mvn verify` is green locally (and on GitHub Actions).
2. App boots locally; `GET /actuator/health` returns `UP`.
3. Flyway baseline migration `V1` runs against PostgreSQL.
4. ArchUnit modularity test passes, encoding the layering rules.

The remaining TASKS.md T0 criteria ("runs on staging, CI deploy green") stay `[EMBER]`-blocked.
