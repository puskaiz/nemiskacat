# T10 — KHPos payment integration design

**Date:** 2026-06-13 · **Task:** B-webshop `TASKS.md` T10 · **Status:** approved (brainstorming)

## Context

Wire the in-house `hu.deposoft:khpos-spring-boot-starter:1.0.0` (Spring Boot 4,
Java 21, validated against the live K&H sandbox) into the checkout. The starter
provides `KhposPaymentService` (init/queryStatus/refund/...), a signed return-URL
controller at `/khpos/return`, and a sealed `KhposPaymentEvent` hierarchy
(Confirmed/Rejected/Reversed/... with `payId` + `orderNo`). Staging keys are
`[EMBER]`-blocked → everything is wired and integration-tested behind a port;
`khpos.enabled=false` until the keys arrive, then the staging test payment runs.

## Decisions

- **Port in the application layer** (dependency inversion): `PaymentGateway`
  (init → `payId` + redirect URL; queryStatus). `integrations/khpos/
  KhposPaymentGateway` implements it on top of the starter
  (`@ConditionalOnBean(KhposPaymentService)`); without keys a
  `DisabledPaymentGateway` activates and the checkout hides the pay button.
- **Payment entity (V6):** `payment(order FK, pay_id UQ, amount_huf, state
  [INITIATED|CONFIRMED|FAILED|REVERSED], result_message, alert bool,
  last_checked_at, …)`. Multiple attempts per order allowed (each init = new row);
  `pay_id` is the correlation key.
- **Flow:** confirmation page (order NEW) → POST `/penztar/fizetes/{clientKey}` →
  `PaymentService.start` → gateway init (orderNo = `NK-{id}`, amount =
  `total_gross_huf`) → save INITIATED → redirect to the bank. Bank → starter
  `/khpos/return` (signature-verified) → events → our listener →
  `PaymentService.applyGatewayResult` (single idempotent entry point). A custom
  `KhposReturnUrlResolver` bean appends `payId` to the configured landing URL
  (`/penztar/fizetes/visszateres`), which resolves payment→order and redirects to
  the confirmation page with a status banner.
- **applyGatewayResult (idempotent, the tested core):**
  - CONFIRMED/SETTLED → payment CONFIRMED; order NEW→PAID. Already PAID → no-op
    (duplicate callback). Order transition impossible (e.g. CANCELLED) or payment
    row missing → **alert** (`payment.alert=true` + ERROR log) = the
    "fizetve, de rögzítés hibázott" branch; real alerting hooks in at T23.
  - REJECTED/FAILED → payment FAILED, order stays NEW (retry allowed).
  - REVERSED → payment REVERSED, order stays NEW.
- **Late/missing callback:** scheduled re-check (`@Scheduled`, 5-min cadence):
  payments INITIATED older than 10 min → `queryStatus` → same
  `applyGatewayResult`; `last_checked_at` updated. Tests call the job directly.
- **ArchUnit relax (documented):** Integrations may access Application — inbound
  adapters (starter events, later courier callbacks) call application services
  exactly like controllers do.

## Error branches covered by integration tests (acceptance)

init creates INITIATED + redirect; confirmed → order PAID; **duplicate
confirmed → no-op**; **rejected (megszakítás) → FAILED, order NEW, retry
creates a new attempt**; **late callback → re-check job flips to PAID**;
**confirmed on CANCELLED order / unknown payId → alert, no crash**. Gateway is
mocked at the port — the bank protocol itself is the starter's tested domain.
The staging test payment (acceptance item 2) runs once the `[EMBER]` keys exist.
