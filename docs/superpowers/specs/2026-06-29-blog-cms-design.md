# Blog (CMS, 1. szelet) — tervdokumentum

- **Dátum:** 2026-06-29
- **Feladat:** `docs/TASKS.md` → T-BLOG (első szelet)
- **Branch:** `worktree-blog-cms`
- **Státusz:** elfogadva (brainstorming), implementációs terv következik

## 1. Cél és háttér

A blogcikkek — a termékekhez hasonlóan — **az adatbázisban** élnek, Markdown
törzzsel, és admin **szerkesztőfelületről** kezelhetők (kézi CRUD). A publikus
blogoldal ebben az appban renderelődik, ajánlott termékekkel.

### Architektúra-döntés (eltérés a CLAUDE.md-től)

A jelenlegi CLAUDE.md #9 + Stack-szakasz a blog forrásaként a **file-alapú**
`content/blog/` Markdownt és PR-alapú publikálást ír elő. Ez a szelet **DB-alapú**
tárolásra és admin-szerkesztőre vált. A tulajdonos (puskaiz@monolith-it.hu)
jóváhagyta. Rögzítés:

- Új ADR: `docs/adr/0008-blog-in-database.md` (file→DB váltás indoklása,
  következményei: nincs deploy a publikáláshoz, egységes admin-élmény, cache-purge
  külön szeletben).
- CLAUDE.md #9 és a Stack-szakasz frissítése a DB-alapú modellre.
- A `content/blog/.gitkeep` megmarad, de már nem forrás.

### Ami NEM változik (CLAUDE.md-megfelelés)

- A blogoldal HTML-je **munkamenet-mentes és cache-elhető** (#2): nincs benne
  felhasználó-/kosárfüggő adat.
- Az ajánlott termékek **elérhetőségi állapottal** renderelődnek, és
  **elfogyott/kifutott esetén automatikusan kimaradnak** (#9).
- `Article` JSON-LD a cikkoldalon (#7).
- Slug-megőrzés: a slug egyedi és stabil, megváltoztatása kerülendő (#7).
- Üzleti logika kizárólag a service-rétegben (#1); a `web` és `api/admin`
  controllerek vékonyak, ugyanazt a service-t hívják.
- Pénz minor unitban, idők UTC-ben tárolva, Europe/Budapest megjelenítés (#6).

## 2. Scope

### Benne (ez a szelet)

- DB-modell: cikk, blog-kategória, cikk↔kategória, cikk↔ajánlott-termék.
- Domain + application réteg (admin CRUD + publikus lekérdezések).
- Admin SPA (Refine) szerkesztő: Markdown editor + élő előnézet, kategória
  multiselect, ajánlott-termék (SKU) választó, borítókép-feltöltés,
  Draft/Published kapcsoló.
- Publikus web: cikklista (lapozva), kategória-szűrt lista, cikkoldal flexmark
  renderrel, `Article` JSON-LD, ajánló-doboz.
- flexmark függőség bevezetése.
- ADR + CLAUDE.md frissítés.

### Kívül (külön, későbbi feladatok)

- AI-cikkgenerálás (Claude API) — külön szelet.
- Meilisearch blog-indexelés — T7 (termékindex) még nincs kész.
- Cache-purge / fordított hivatkozási lista (termék-állapotváltáskor érintett
  blog-URL-ek purge-ölése).
- Régi WordPress lapozó-URL kompatibilitás (`/blog/page/{n}`).
- Ütemezett (scheduled) publikálás.
- Tömeges import/migráció a régi blogból.

## 3. Adatmodell — Flyway `V22__blog.sql`

### `blog_post`
| oszlop | típus | megjegyzés |
|---|---|---|
| `id` | bigserial PK | |
| `slug` | text, UNIQUE, NOT NULL | URL-azonosító, stabil |
| `title` | text, NOT NULL | |
| `excerpt` | text, NULL | lista-kártya + meta fallback |
| `body_markdown` | text, NOT NULL | nyers Markdown forrás |
| `cover_image_key` | text, NULL | hash-elt storage-kulcs (`/media/...`) |
| `status` | text, NOT NULL | `DRAFT` \| `PUBLISHED` |
| `published_at` | timestamptz, NULL | publikáláskor áll be (UTC) |
| `seo_title` | text, NULL | fallback: `title` |
| `seo_description` | text, NULL | fallback: `excerpt` |
| `created_at` | timestamptz, NOT NULL | |
| `updated_at` | timestamptz, NOT NULL | |

### `blog_category`
| oszlop | típus | megjegyzés |
|---|---|---|
| `id` | bigserial PK | |
| `name` | text, NOT NULL | |
| `slug` | text, UNIQUE, NOT NULL | `/blog/kategoria/{slug}` |

### `blog_post_category` (M:N)
`blog_post_id` FK, `blog_category_id` FK, PK a páron.

### `blog_post_product` (cikk → ajánlott termék)
`blog_post_id` FK, `sku` text NOT NULL, `sort_order` int NOT NULL.
PK: `(blog_post_id, sku)`. A termék állapota **nem** tárolódik itt — renderkor
származtatott. SKU lehet variáns-SKU is.

## 4. Domain réteg — `domain/blog`

- `BlogPost`, `BlogCategory`, `PublicationStatus (DRAFT, PUBLISHED)`.
- Szabályok:
  - Slug-formátum validáció (kebab-case, üres tiltva).
  - `publish()`: ha még nincs `publishedAt`, beáll (UTC).
  - `unpublish()`: státusz vissza `DRAFT`-ra (a `publishedAt` megőrizhető).
  - Slug-ütközés tiltása (alkalmazás-szinten az egyediségi index + barátságos
    hiba; lásd 7. hibakezelés).
- Tiszta egységek, controller- és perzisztencia-függetlenek, unit-tesztelhetők.

## 5. Application réteg — `application/blog`

### `BlogAdminService`
- Cikk CRUD (create/update/delete), kategória-hozzárendelés, ajánlott-SKU lista
  kezelése (sorrenddel).
- Kategória CRUD.
- `renderPreview(markdown) -> html`: ugyanaz a flexmark-pipeline, mint a publikus
  oldalon → **render-parity** garantált.
- Auditnaplóba ír (mint az ár-/rendelésmódosítás, a meglévő audit-infrastruktúrán
  át).

### `BlogQueryService` (publikus, olvasás)
- `publishedList(page, size)` — csak `PUBLISHED`, `published_at` szerint csökkenő.
- `publishedListByCategory(categorySlug, page, size)`.
- `getPublishedBySlug(slug)` — draft/ismeretlen → üres (web 404).
- Az ajánlott termékeket a `CatalogQueryService`-en keresztül tölti, a tárolt
  SKU-sorrendben, és **elfogyott/kifutott kihagyással + állapottal** adja vissza
  (a kategóriaoldal termékkártya-nézetével azonos `view`-típus).
- A cikk törzsét flexmarkkal HTML-re rendereli (a `web` réteg kész HTML-t kap).

### flexmark
- Új `pom.xml` függőség (a CLAUDE.md Stack már nevesíti). Központi
  `MarkdownRenderer` komponens, hogy admin-preview és publikus render egyazon
  konfigurációt használja (parity).

## 6. Publikus web — `web` (Thymeleaf, cache-elhető)

Új `BlogController` (vékony), a `fragments/layout.html` design-rendszerrel.

| Útvonal | Nézet |
|---|---|
| `GET /blog` | Cikklista: kártyák (borítókép, cím, excerpt, dátum, kategóriák), lapozás. |
| `GET /blog/kategoria/{slug}` | Kategória-szűrt lista (ismeretlen kategória → 404). |
| `GET /blog/{slug}` | Cikk: flexmark→HTML törzs, `Article` JSON-LD (cím, `datePublished`, kép, leírás), OG-meta a borítóképpel, **ajánló-doboz a cikk törzse után** (a publikált+elérhető SKU-kból, termékkártya-stílus, állapottal). Draft/ismeretlen → 404. |

- `Cache-Control` fejlécek mikro-cache-re készen; a HTML cookie-független és
  byte-azonos két látogatónak (#2).
- Sablonok: `templates/blog/list.html`, `templates/blog/post.html` (+ a kártya
  fragment újrahasznosítása, ahol lehet).

## 7. Admin — `api/admin` + `admin-ui`

### REST (`api/admin`)
- `BlogPostController` → `/api/admin/blog/posts` (CRUD + ajánlott-SKU + kategória
  hozzárendelés + borítókép-feltöltés a `ProductImageController` multipart-mintája
  szerint → hash-elt storage → `/media/...`).
- `BlogCategoryController` → `/api/admin/blog/categories` (CRUD).
- `POST /api/admin/blog/preview` → Markdown→HTML előnézet (render-parity).
- Minden mutáció auditnaplóba kerül.
- Hibák RFC 9457 problem+json (a meglévő `AdminExceptionHandler` mintájára):
  - slug-ütközés → **409**
  - ismeretlen SKU az ajánlóban → **422**
  - nem létező cikk/kategória → **404**

### Refine SPA (`admin-ui/src/pages/blog`)
- Lista + szerkesztő oldal.
- Mezők: cím, slug, excerpt, SEO cím/leírás, **Markdown szerkesztő + élő
  előnézet** (a `preview` endpointot hívja), kategória multiselect, **ajánlott
  termékek** SKU-választó (a meglévő admin termékkereső újrahasznosításával),
  borítókép-feltöltés, Draft/Published kapcsoló.
- A blog admin **nincs** a termékszerkesztő feature-flag mögött (az flag a
  Woo-átállásig csak a termékekre vonatkozik); a blog új, saját felület.
- Mivel az `admin-ui`-ban nincs Markdown-editor függőség, könnyű megoldást
  vezetünk be: `textarea` + a szerver `preview` endpointjából táplált előnézet-tab.

## 8. Hibakezelés

- Admin API: RFC 9457 problem+json (lásd 7.).
- Publikus oldal: ismeretlen vagy draft slug → 404 oldal; ismeretlen kategória →
  404.
- Borítókép-feltöltés: nem kép / túl nagy fájl → problem+json a meglévő média-
  validációval összhangban.

## 9. Tesztelés

- **Domain unit:** státuszátmenet (publish/unpublish), slug-validáció.
- **Application:**
  - flexmark **render-parity** (admin preview HTML == publikus render HTML).
  - Ajánló-doboz: elfogyott/kifutott kihagyás + helyes állapot a megmaradtakon.
  - Publikus lekérdezések: draft nem jelenik meg, kategória-szűrés, lapozás.
- **Integráció (Testcontainers Postgres):** CRUD, slug-ütközés (409), kategória-
  hozzárendelés, ajánlott-SKU sorrend, borítókép-feltöltés.
- **Web:** publikus cikk-HTML cookie nélkül byte-azonos (cache-elhetőség);
  `Article` JSON-LD validál; draft → 404.

## 10. Elfogadási kritérium (a szeletre)

- Admin felületről új cikk létrehozható (Markdown + kategória + ajánlott SKU +
  borítókép), piszkozatként nem látszik publikusan, publikálás után megjelenik a
  `/blog` listán és a `/blog/{slug}` oldalon.
- A cikkoldal `Article` JSON-LD-je validál; az ajánló-dobozból egy elfogyottá tett
  termék a következő rendereléskor kimarad.
- Zöld tesztek (domain/application/integráció/web); futtatható állapot.
- ADR + CLAUDE.md frissítve.
