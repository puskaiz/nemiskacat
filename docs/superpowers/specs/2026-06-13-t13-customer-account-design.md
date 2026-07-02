# T13 — Customer account + WordPress password migration design

**Date:** 2026-06-13 · **Task:** B-webshop `TASKS.md` T13 · **Status:** approved (brainstorming)

## Context

Customer accounts on Spring Security (session cookie, CLAUDE.md #3 — no JWT), and
**migration of the ~4640 WooCommerce customers** from the local WP DB. WP stores
phpass portable hashes (`$P$B…`, 8192 iterations — verified against the live dump,
incl. a real account). Strategy (overrides TERV §7's "force password reset"):
**migrate the hashes and upgrade on login** — log in against the legacy hash, then
Spring Security transparently re-encodes to bcrypt (`UserDetailsPasswordService`).

## Decisions

- **Who migrates:** role `customer` (4640). Admins (14) and subscribers (535) are
  excluded (staff / newsletter-only — not shop logins).
- **Hash handling:** migrated password stored as `{wp}$P$B…`. A
  `DelegatingPasswordEncoder` (default `{bcrypt}`) routes `{wp}` to a
  `WordPressPasswordEncoder` (phpass `matches`; `encode` throws — we never mint WP
  hashes). `upgradeEncoding` is true for `{wp}` → on a successful login Spring
  Security calls `UserDetailsPasswordService.updatePassword`, which re-encodes to
  `{bcrypt}` and persists. Second login already verifies against bcrypt.
- **Migration path** (consistent with T4): `scripts/woo-export/export-customers.py`
  → JSON (email, login, display/first/last name, wp_user_id, registered, the
  `$P$` hash) → `CustomerImporter` (idempotent upsert by `wp_user_id`/email),
  run via the `import` profile. PII stays out of the repo; the JSON is local-only.
- **URLs (WP-preserved):** account hub `/fiokom` (WooCommerce slug). Anonymous →
  login + register page; authenticated → account. Processing URLs are new:
  `POST /fiokom/belepes` (Spring Security), `POST /fiokom/regisztracio`,
  `POST /fiokom/kilepes` (logout).
- **Cart merge:** an `AuthenticationSuccessHandler` merges the guest cart
  (`nk_cart`) into the customer's cart on login (claims/merges via
  `cart.user_id`, prepared in T8), then points the cookie at the surviving cart.
- **Registration:** email + password → `{bcrypt}`; duplicate email rejected;
  validation (email format, password length).
- **Account page:** profile + order history (orders linked by email,
  case-insensitive).

## Schema (V7)

```
customer(id, wp_user_id UQ NULL, email UQ (citext/lower), password_hash,
         first_name, last_name, display_name, enabled, role[CUSTOMER],
         created_at, updated_at)
```
`cart.user_id` already exists (T8) → FK semantics by convention.

## Security wiring

- `CustomerUserDetailsService implements UserDetailsService, UserDetailsPasswordService`
  (loads by email; `updatePassword` persists the upgraded hash).
- `PasswordEncoder` bean = delegating(`bcrypt` default + `wp`).
- `SecurityConfig` (extends T8 CSRF): `formLogin(loginPage=/fiokom,
  loginProcessingUrl=/fiokom/belepes, usernameParameter=email)`, `logout`,
  `authenticationSuccessHandler` = cart-merge. Catalog/cart/checkout stay
  permitAll; the account view requires authentication. Forms carry the `_csrf`
  hidden field (cookie-token repo from T8).

## Tests (TDD)

- `WordPressPasswordEncoderTest`: phpass vectors (from the WP container) match;
  wrong password fails; non-`$P$` input fails gracefully; `encode` throws.
- `CustomerImporterTest`: idempotent upsert; hash stored `{wp}…`; admin/subscriber
  rows excluded by the exporter (script-level, sampled).
- Security integration (MockMvc + Testcontainers): login with the legacy
  password succeeds; the stored hash is rewritten to `{bcrypt}` afterward
  (upgrade-on-login); wrong password 401; registration creates a `{bcrypt}` user;
  duplicate email rejected; guest cart merges into the account on login; order
  history lists the customer's orders.
- Live check: log in as the real migrated account against its `$P$` hash.

## Out of scope (T13 follow-ups)

Password reset (needs T12 email), address book + checkout prefill, GDPR
export/delete, "remember me". Marked here so they are not forgotten.
