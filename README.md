# nemiskacat.hu — webshop (B track)

Self-developed Spring Boot webshop replacing WooCommerce. Customer side is
Thymeleaf + htmx; admin and POS are a REST API + SPA. See `CLAUDE.md` for the
binding engineering rules, `docs/SPEC.md` for the work package, `docs/TASKS.md`
for the ordered task list, and `docs/TERV.md` for the full system design.

## Stack

- Java 21, Spring Boot 4.1.0, Maven (single modular-monolith module)
- PostgreSQL + Flyway
- Thymeleaf + htmx (customer side); REST + SPA (admin/POS, later)

## Module layout (packages, boundaries enforced by ArchUnit)

```
domain/        entities + business rules
application/   service layer — the single source of truth for business logic
web/           thin Thymeleaf controllers (customer side + blog)
api/           thin REST controllers (admin, POS, static-page islands, feeds)
integrations/  starters + adapters (Kulcs-Soft, payment, courier, marketing)
config/        cross-cutting configuration
```

## Local development

Requires JDK 21, a local PostgreSQL, and Docker (for Testcontainers in tests).

```bash
# Run the app (expects PostgreSQL on localhost:5432, db/user/pw: webshop;
# override via .env — DB_URL, DB_USERNAME, DB_PASSWORD):
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Build + run all tests (uses Testcontainers PostgreSQL):
mvn verify
```

- Health: `GET /actuator/health`
- App info (api layer demo): `GET /api/info`
- Home (web layer demo): `GET /`

Copy `.env.example` to `.env` for local overrides; `.env` is gitignored.

## Deployment (Railway)

The repo is self-contained: `Dockerfile` builds the backend and the admin SPA in
one multi-stage build, and the internal `hu.deposoft` starters (KHPos, Billingo)
resolve from the in-repo `libs/` Maven repository — no external registry or build
secret is needed. `railway.json` selects the Dockerfile builder and health-checks
`/actuator/health`.

Steps:

1. Add a **PostgreSQL** plugin to the Railway project.
2. On the app service, set these variables (values reference the Postgres plugin):

   ```
   DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   DB_USERNAME=${{Postgres.PGUSER}}
   DB_PASSWORD=${{Postgres.PGPASSWORD}}
   ```

   `PORT` is injected by Railway automatically (the app binds `server.port=${PORT}`).
   Add integration secrets (KHPos, Billingo, Instagram, …) as separate variables when
   those features are enabled — never commit them.
3. Add a **Volume** to the app service for uploaded images. `FilesystemStorageService`
   writes content-addressed image files under `STORAGE_DIR` and `MediaController`
   serves them at `/media/**`; the container filesystem is ephemeral, so without a
   volume every upload is lost on redeploy. Mount the volume at `/data` and set:

   ```
   STORAGE_DIR=/data/uploads
   ```

   To seed the volume with existing images (the local `data/uploads/` tree), stream
   them in over Railway SSH — the files must live on the volume, they are not in git:

   ```bash
   # one-time: register an ed25519 key + an SSH config alias for the service
   railway ssh keys add -k ~/.ssh/id_ed25519.pub
   railway ssh config --alias railway-webshop -i ~/.ssh/id_ed25519 --service <app-service>
   # copy (COPYFILE_DISABLE avoids macOS AppleDouble ._* sidecars); idempotent
   COPYFILE_DISABLE=1 tar cf - -C data uploads \
     | ssh -o IdentitiesOnly=yes -i ~/.ssh/id_ed25519 railway-webshop 'tar xf - -C /data'
   ```

   Keys are the SHA-256 of the content, so re-running just overwrites identical files.
4. Deploy. Flyway runs the migrations on first boot; the health check turns green
   once `/actuator/health` reports `UP` (the check includes a DB probe, so it only
   passes once Postgres is reachable).

Tests are **not** run during the image build (they need a Testcontainers Docker
daemon that the build sandbox lacks); run `mvn verify` in CI instead.

> **Data migration.** To move an existing local database into the Railway Postgres,
> dump the `public` schema (including `flyway_schema_history`, so the app sees the
> migrations as already applied) and restore it via the plugin's public proxy URL —
> version-matched client, e.g. `docker run --rm postgres:18 …`. Exclude any
> server-local extensions the stock Railway Postgres lacks (e.g. `timescaledb`).

## Status

- **T0** project skeleton — local build green; CI deploy + staging `[EMBER]`-blocked.
- **T1** WooCommerce catalog PoC — report in `docs/poc/`.
- **T3** catalog data model — `V2__catalog.sql` + JPA entities + derived stock-status
  calculator (unit-tested for every transition).
- **T8–T10** cart + session, checkout + fees/VAT, KHPos payment (sandbox live).
- **T13** customer accounts — Spring Security login; WooCommerce customers migrated
  with phpass hashes that upgrade to bcrypt on first login; guest-cart merge.
- **T4** idempotent catalog importer — source-agnostic (`CatalogSource` port; JSON
  export from the local Woo DB now, REST later). Real catalog loaded: 430 products,
  887 variants, 2067 images; re-run = pure update. See `scripts/woo-export/README.md`.

`mvn verify` green (109 tests).

Design specs under `docs/superpowers/specs/`; decisions in `docs/adr/`.
