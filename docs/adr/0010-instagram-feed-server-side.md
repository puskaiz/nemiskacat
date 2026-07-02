# 10. Instagram feed via a server-side own-account adapter (Graph API + RestClient)

Date: 2026-07-01

## Status

Accepted

## Context

The current WooCommerce site renders an Instagram feed in the sidebar of some
pages (e.g. `/ombre-butorfestes/`) via a WordPress plugin. We want the same
"latest posts from the shop's Instagram" box in the new app. Three forces shape
the decision:

1. **Instagram's platform model.** The old Instagram Basic Display API (the
   "paste an embed" era) was shut down at the end of 2024. The only supported
   read path now is the **Instagram API with Facebook Login** (Graph API, v22.0
   as of 2026). It requires a **Business/Creator** account linked to a Facebook
   Page, an OAuth flow, and a **long-lived access token (~60 days) that does not
   auto-refresh** — it must be refreshed by us before expiry or the feed goes
   silently blank.

2. **Access tier — App Review is avoidable for our case.** Meta gates each
   permission by access tier. **Advanced Access** (serving *external* users'
   accounts) requires App Review + Business Verification. **Standard Access**
   (only accounts that hold a *role on our app*) does not. We only ever display
   the *shop's own* account, so we assign it a role on our Meta app and stay on
   **Standard Access with `instagram_basic` — no App Review, no full Business
   Verification** (Meta may still prompt for a one-time business-document
   upload). This removes the long-pole that would otherwise dominate the work.

3. **Our architecture rules.** Public pages must be session-free and cacheable
   (rule #2); the only sanctioned browser-side island is the session/cart sliver
   (rule #3); secrets never live in the repo (Tiltások). A naive
   "fetch in the Thymeleaf controller per render" would couple a cacheable page
   to a slow, rate-limited, failure-prone external call, and a browser-side
   Graph API call is impossible anyway (it needs a secret token that must never
   reach the browser).

## Decision

Build a **server-side own-account Instagram adapter** under
`integrations/instagram`, shaped like the existing integration adapters
(e.g. Kulcs-Soft).

- **Transport: Spring `RestClient`** against the Graph API REST endpoints
  (`GET /{ig-user-id}/media?fields=id,caption,media_type,media_url,permalink,
  thumbnail_url,timestamp`). No third-party Instagram library — the API is plain
  REST+JSON and a typed client (RestFB) or a private-API wrapper buys us nothing
  here. **No new dependency.**
- **Standard Access, own account only.** The shop's IG Business/Creator account
  is assigned a role on our Meta app; we request only `instagram_basic`.
- **Token lifecycle is the load-bearing part.** The long-lived access token is
  stored as a **secret** (env / secret store, never the repo). A **scheduled
  job** refreshes it well before the ~60-day expiry and alerts on refresh
  failure (a dead token = an empty feed).
- **Fetch out-of-band, cache server-side.** A scheduled fetch pulls the latest N
  posts into a server-side cache (DB or cache store). Public pages render from
  *our copy*, never calling Instagram during a page render — so they stay
  session-free and cacheable (rule #2).
- **Render in Thymeleaf, no browser-side Instagram JS.** The sidebar fragment is
  server-rendered from the cached posts. No third-party script, so the islands
  model (rule #3) is untouched. CSP only needs to allow Instagram's **image**
  CDN origin (or we proxy/cache thumbnails per rule #8), not any script origin.
- **Graceful degradation.** If the token is dead or a fetch fails, the box
  renders the last good cached copy (or hides itself) — it must never break or
  block the page.

## Considered alternatives

- **`instagram4j` (private-API wrapper).** Rejected. It reverse-engineers the
  *mobile* app API, logs in as the user with stored credentials, violates
  Instagram's ToS, risks the account being flagged/banned, and breaks whenever
  Instagram changes signatures. Built for bots, wrong for a production feed.
- **Third-party JS widget (Elfsight, Behold, LightWidget, …).** Rejected as the
  default; kept as a documented fallback. Fastest to ship (they hold the token
  and inherit their own App Review), but it injects uncontrolled third-party
  JS, loosens CSP, typically sets cookies (consent obligation), adds a runtime
  third-party dependency and a recurring fee — all against the islands model.
  If we ever need it fast, allowing it is a *conscious, documented* exception to
  rule #3, not the standard path.
- **`RestFB` (typed Graph API client).** Rejected in favour of plain
  `RestClient`. For a read-only own-account media feed it only saves a few DTOs
  while adding a dependency; `RestClient` keeps the surface area minimal and the
  caching/error handling fully in our control.

## Consequences

- **Human prerequisites `[EMBER]`** before any code is useful: convert the shop
  account to Business/Creator, link it to a Facebook Page, create the Meta app,
  assign the account a role on it, and run the OAuth flow once to mint the
  long-lived token. These are Meta account-setup steps, not coding.
- The **token-refresh job is security- and reliability-critical**: it owns a
  secret and a silent-failure mode, so it gets monitoring/alerting (ties into
  T23 operational basics).
- No new server dependency; no `admin-ui` change. The feed is a self-contained
  integration adapter plus a Thymeleaf fragment.
- Public pages remain byte-stable and cacheable; the feed adds no per-request
  external call and no per-user data.
- Future option: surface the cached posts as a small read API if other surfaces
  (e.g. a marketing page) want the same data.

## Addendum (2026-07-01): rendered as a sidebar *block*, not a standalone widget

Between this ADR and implementation, `main` shipped a **blog sidebar block CMS**
(`sidebar_block` table, `SidebarQueryService`, `BlockType` = AUTHOR / CATEGORIES /
CTA / CONTACT / SOCIAL, rendered by `fragments/blog.html :: sidebar` on the blog
list + article pages, with admin management). That replaced the empty `<aside>`
this ADR assumed. Rather than run a second, parallel sidebar mechanism, the feed
is rendered as a new **`INSTAGRAM` block type** within that CMS:

- `BlockType.INSTAGRAM`; `SidebarQueryService` reads only the block's `content`
  JSON (`{title, count}`) and pulls the posts **live from `InstagramFeedQuery`**
  (the DB cache) — no live external call on render (rule #2 upheld).
- `fragments/blog.html` gains an `INSTAGRAM` `<section>`; an empty/disabled feed
  renders **no** section. Seeded by `V26__instagram_sidebar_block.sql`.

**Unchanged:** everything in the Decision above — the server-side `RestClient`
adapter, DB cache, persisted token + refresh job, scheduler, `instagram.enabled`
flag (off by default), and the no-third-party-JS / cacheable / secrets rules. The
only revision is the last mile (how cached posts reach the sidebar).

**Revised consequence:** the earlier "no `admin-ui` change" note now has a caveat —
a minimal `INSTAGRAM` case was added to admin content-validation, but **admin-ui
editing of the Instagram block is deferred to the sidebar CMS's Phase 2**; today
the block is seeded and can be enabled/disabled but not composed in the admin UI.
