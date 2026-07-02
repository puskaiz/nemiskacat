# WordPress tartalmi oldalak → DB-alapú oldal-CMS — tervdokumentum

- **Dátum:** 2026-07-02
- **Feladat:** a `nemiskacat` WordPress statikus tartalmi oldalainak átemelése egy DB-alapú oldal-CMS-be, hogy az egész oldal a Spring Boot appból működjön (a CDN-re költöztetés későbbi lépés)
- **Branch:** `worktree-content-pages`
- **Előzmény:** a bevált kétlépéses WordPress-import minta (termék-/rendelés-/**workshop**-/blog-import). Ez a feladat a workshop-migrációt tükrözi legszorosabban: Elementorral épült oldalak → összeállított HTML → DB → render → admin.
- **Státusz:** elfogadva (brainstorming), implementációs terv következik

## 1. Cél és háttér

A `nemiskacat` WordPress helyileg Dockerben fut (`wp_db` konténer, DB `client7002dbnem`, táblaprefix `guxdop_`), közvetlenül elérhető. A ~49 WP-oldalból a felmérés szerint **41 Elementorral épült**. Ez a szelet a tulajdonos által kiválasztott **10 tartalmi oldalt** emeli át (mind Elementor, mind `post_parent = 0`, azaz lapos — nincs hierarchia):

`butorfestes-kezdoknek`, `rolunk`, `latvanymuhely`, `kapcsolat`, `csapatunk`, `itt-hallottal-meg-rolunk`, `butorfestes-otletek`, `gyakran-ismetelt-kerdesek`, `altalanos-szerzodesi-feltetelek`, `adatvedelem`.

A bevált kétlépéses mintát követjük:

1. **Exporter** (Python a `scripts/woo-export/` alatt): `docker exec wp_db mysql …` közvetlenül a WP DB-re, az Elementor-widgetfát dokumentumsorrendben bejárva összeállított HTML-t ír egy JSON-pillanatképbe (csak olvas).
2. **Importer** (Java): a JSON-t beolvassa és **idempotensen upsertel** a Postgresbe, szerveroldali HTML-sanitálással (jsoup safelist), az inline képeket a hash-elt storage-ba tölti.

### Elfogadott döntések (brainstorming)

- **HTML-natív tárolás** (nincs Markdown-oszlop): a forrás Elementor-HTML, ezért a `body_html` közvetlenül tárolódik sanitálva. A blog-tól (Markdown-forrás) ebben tér el; a workshop `descriptionHtml`-t tükrözi.
- **Elementor-összeállítás újrahasznosítás:** az `export-workshops.py` `walk()` bejárója (heading, text-editor, toggle/accordion, media/image-carousel, image widgetek) szinte változatlanul átvehető.
- **Gyökér-URL megőrzés:** az oldalak a WP-hez hűen a gyökér `/{slug}` útvonalon szolgálódnak ki (SEO, #7).
- **Egységes gyökér-feloldás:** mivel a `BlogController` már birtokolja a `/{slug}` mappingot, a `/{slug}` feloldást egy közös vezérlőbe emeljük ki, amely **előbb oldalt, majd blogposztot** próbál.
- **Konfigurálható slug-lista:** az exporter egy szerkeszthető slug-listából dolgozik, hogy élesítés előtt egy (bővíthető) listán újra lefuttatható legyen.
- **Scope-korlát:** csak tartalmi oldalak. A WooCommerce-funkciós oldalak (kosár/pénztár/fiók/shop) az app natív útvonalain élnek; a kampány-/hírlevél-oldalak (űrlappal) és a menü-bekötés külön szeletek.

## 2. CLAUDE.md-megfelelés

- **#1 Üzleti logika a service-rétegben:** a vezérlők vékonyak; a feloldás/render logika a query-service-ben.
- **#2 Munkamenet-mentes, cache-elhető HTML:** a nyilvános oldallap felhasználó-/kosárfüggő adatot nem tartalmaz; a belépett állapot kizárólag a közös kliensoldali sziget.
- **#4 Idempotencia:** kétszeri futtatásra duplikáció nélkül upsertel; kulcs az `external_id` (WP page id), tartalék a slug. WP-oldali módosítás a következő futáskor átjön.
- **#7 Slug-megőrzés:** a WP `post_name` 1:1 átvéve, megváltoztatása tilos (SEO).
- **#6 Idő:** UTC-ben tárolva, megjelenítés Europe/Budapest.
- **#8 Képek:** storage-ba hash-elt, megváltoztathatatlan kulccsal; az app képfeldolgozást nem végez.
- **#9 Sanitálás:** minden írási út (import és admin-mentés is) szerveroldali HTML-sanitáláson megy át (jsoup safelist); Draft/Published állapot.
- **#10 Nyelv:** tartalom magyar; kód/commit/komment angol.
- **Tiltások:** a WP-n csak olvasás; éles DB-művelet csak ember; az exporter titkokat nem olvas.

## 3. Adatmodell — `V35__content_page.sql`

`content_page` tábla:

| oszlop | típus | megjegyzés |
|---|---|---|
| `id` | bigserial PK | |
| `external_id` | bigint, unique | WP page id — idempotens upsert kulcsa |
| `slug` | text, unique, not null | WP `post_name`, megváltoztathatatlan (#7) |
| `title` | text, not null | |
| `body_html` | text, not null | összeállított + sanitált HTML |
| `status` | text, not null | `PublicationStatus` újrahasznosítva (DRAFT/PUBLISHED) |
| `seo_title` | text, null | Yoast `_yoast_wpseo_title` |
| `seo_description` | text, null | Yoast `_yoast_wpseo_metadesc` |
| `published_at` | timestamptz, null | |
| `created_at` / `updated_at` | timestamptz, not null | |

Nincs `body_markdown` (HTML-natív). Nincs hierarchia-oszlop (a 10 oldal lapos — YAGNI). Legutóbbi migráció V34 → ez **V35**.

## 4. Exporter — `scripts/woo-export/export-pages.py`

Az `export-workshops.py` mintájára, a `walk()` Elementor-bejárót újrahasznosítva. `wp_db` / `client7002dbnem`, prefix `guxdop_`. Egy **szerkeszthető `SLUGS` lista** vezérli (a 10 slug) — ez a „lista alapján újrafuttatható" követelmény.

Oldalanként olvasott adat:
- `guxdop_posts` (a slug-listára szűrve, `post_type='page'`): `ID` (externalId), `post_name` (slug), `post_title`, `post_status`, `post_date_gmt`.
- **Törzs:** `guxdop_postmeta._elementor_data` → a widgetfa dokumentumsorrendű bejárása → egyetlen összeállított `bodyHtml`. Elementor nélküli oldalnál tartalék a `post_content`.
- **SEO:** `_yoast_wpseo_title`, `_yoast_wpseo_metadesc` (postmeta).

Kimenet: egyetlen `pages.json` a `SourcePages` DTO-alakkal (`{ "pages": [ … ] }`). Az exporter **képet nem tölt le** és HTML-t nem sanitál — azt a Java importer végzi. A `scripts/woo-export/README.md` frissül; kísérő `test_export_pages.py` (fixált `mysql`-kimenetre, DB nélkül) a `test_export_orders.py` mintájára.

## 5. Source DTO-k — `integrations/wordpress`

- `SourcePages(List<SourcePage> pages)`
- `SourcePage(long externalId, String slug, String title, String bodyHtml, String status, OffsetDateTime publishedAt, String seoTitle, String seoDescription)`

Jackson 3 (mint a `SourceBlog`), JSON-fájlból deszerializálva.

## 6. Importer — `application/page/PageImporter`

Idempotens, a `BlogImporter`/`WorkshopImporter` mintájára; `PageImportReport`-ot ad (importált / frissített / kihagyott / hibák). `@Transactional`.

Lépések oldalanként:
1. **Upsert `external_id` szerint** (tartalék: slug). Létezőt frissít (cím, törzs, SEO, státusz), újat létrehoz. **A slug soha nem változik** (#7).
2. **Inline kép-letöltés a storage-ba:** a `body_html` inline `<img>` képei közül a WP-uploads hosztra mutatók a meglévő `ImageFetcher` + `StorageService` mechanizmussal töltődnek (content-hash kulcs), a `src` `/media/<kulcs>`-ra átírva; külső URL érintetlen. A blog-importer inline-kép kezelését újrahasznosítjuk.
3. **Sanitálás:** `BlogHtmlSanitizer` a `body_html`-en (jsoup safelist) — közös sanitáló minden írási úton (#9).
4. **Státusz + `published_at`:** `publish`→`PUBLISHED` (+`published_at = post_date_gmt`), `draft`→`DRAFT`.
5. **Slug-ütközés-ellenőrzés:** ha a slug egy létező blogposzttal ütközik, figyelmeztetés a riportba (a feloldásban az oldal élvez elsőbbséget — l. 7.).

Idempotencia: az `external_id`-kulcs miatt kétszeri futtatás nem duplikál; az újrafuttatás a WP aktuális állapotára frissít. A storage hash-elt kulcsai miatt változatlan kép nem töltődik újra.

## 7. Trigger — `PageImportRunner` (`@Profile("import-pages")`)

A `BlogImportRunner` mintájára: az app `import-pages` profillal és `--webshop.import.pages-file=<pages.json>` paraméterrel indítva betölti a pillanatképet, logolja a riportot, majd kilép (hibák esetén nem-nulla kóddal). **Éles DB-művelet csak ember;** a CI/staging futtathatja. `application-import-pages.yml` a workshop-profil mintájára.

## 8. Nyilvános render + útvonal (az egységesítés)

**Jelenlegi állapot:** a `BlogController` birtokolja a `@GetMapping("/{slug}")` mappingot (a blogposztokat a gyökéren szolgálja ki, 404 ha nincs poszt). Két vezérlő nem mappelheti ugyanazt a `/{slug}`-ot (indulási hiba).

**Megoldás:** új **`RootSlugController`** birtokolja a `@GetMapping({"/{slug}", "/{slug}/"})`-ot, és **előbb oldalt, majd blogposztot** old fel (404, ha egyik sincs). A `/{slug}` handler **kikerül a `BlogController`-ből** (az megtartja a `/blog` és `/blog/kategoria/{slug}` útvonalakat). Az elsőbbség dokumentált; az import figyelmeztet slug-ütközésre.

- Az egzakt útvonalak (`/blog`, `/product/{slug}`, `/termekkategoria/{slug}`, `/workshopok`) továbbra is nyernek a `/{slug}` fölött (Spring path-matching: specifikusabb minta nyer).
- Nyilvános render: `templates/page.html` a `shared/` layouttal; munkamenet-mentes, cache-elhető HTML (#2); `WebPage`/`Article` JSON-LD.
- `PageQueryService.getPublishedBySlug(slug)` — csak publikált oldalt ad vissza; a `ContentPage`-nek nincs lusta kollekciója, így nincs OSIV-materializálási teendő (szemben a blog kategóriáival).

## 9. Admin (Refine SPA)

- **`PageAdminController`** (REST) a `BlogPostController` mintájára: lista (lapozott), get-by-id, create, update, delete.
- **admin-ui „Pages" resource:** lista (lapozott), create/edit (TipTap WYSIWYG, Draft/Published, SEO-mezők), read-only show. A meglévő blog-poszt admin szerkesztőt tükrözi.
- **Minden írási út újra-sanitál szerveroldalon** (jsoup safelist), #9 szerint. Publikálás deploy nélkül.

## 10. Hibakezelés

- Hiányzó/üres slug → oldal kihagyva, riportba (az import nem áll le).
- Letölthetetlen inline kép → az oldal importálódik, a kép-hivatkozás változatlanul marad, figyelmeztetés a riportba.
- Üres/hibás `_elementor_data` → tartalék `post_content`; ha az is üres, az oldal kihagyva, riportba.
- Ismeretlen widget-típus a bejárásban → kihagyva (best-effort), a többi szekció folytatódik.

## 11. Tesztelés

- **Importer (Testcontainers Postgres):**
  - Idempotencia: ugyanazt a `SourcePages`-t kétszer importálva nincs duplikáció; a módosított cím a második futáskor felülír; a slug változatlan.
  - Sanitálás: nem engedélyezett elem/attribútum kiszűrve.
  - Inline kép: WP-uploads URL letöltődik a storage-ba és `/media/<kulcs>`-ra íródik át; külső URL érintetlen.
  - Státusz-leképezés + `published_at` (UTC); draft nem látszik a publikus lekérdezésben.
- **`RootSlugController`:** oldal-találat rendereli az oldalt; poszt-találat a posztot; egyik sem → 404; oldal-vs-poszt elsőbbség (azonos slug esetén az oldal nyer).
- **Render:** a publikus oldallap byte-stabil és nem hordoz munkamenet-/kosáradatot (#2).
- **Admin CRUD IT** a `BlogPostControllerIT` mintájára; az admin-mentés sanitálása igazolt.
- **Exporter:** minta-fixtúrás Python-teszt (`test_export_pages.py`) az Elementor-JSON → összeállított HTML alak helyességére (DB nélkül).

## 12. Elfogadási kritérium

- Az `export-pages.py` a helyi `wp_db`-re lefuttatva a 10 oldalról érvényes `pages.json`-t ad (riport a darabszámról), a slug-listából vezérelve.
- Az app `import-pages` profilban a `pages.json`-t betöltve az oldalak megjelennek a DB-ben; a publikáltak a megőrzött gyökér-slug URL-en (`/{slug}`) renderelnek, helyi `/media`-képekkel; a draftok publikusan rejtve.
- **Kétszeri import duplikáció nélkül**; a WP-ben módosított oldal a következő importnál frissül; a slug soha nem változik.
- A blogposztok gyökér-URL-jei változatlanul működnek (a `RootSlugController` alatt).
- Az adminban az oldalak listázhatók/szerkeszthetők (WYSIWYG, Draft/Published), a mentés sanitál.
- Zöld tesztek (importer + render/routing + admin integráció + exporter egység); futtatható állapot.
- **ADR** rögzíti a döntést: „tartalmi oldalak Postgresben; egységes gyökér-slug feloldás (oldal → blogposzt)".

## 13. Scope-on kívül (későbbi szeletek)

- Fejléc/lábléc **navigációs menü** bekötése (a `shared/` fragment koncern).
- **Kampány-/hírlevél-oldalak** űrlapokkal (marketing-funnel, külön kezelés).
- **Funkciós oldalak** (kosár/pénztár/fiók/shop) — az app natívan kezeli.
- **CDN**-re költöztetés.
- Oldal-hierarchia / szülő-gyerek (a jelenlegi 10 oldal lapos).
- Inkrementális/ütemezett szinkron (most kézi, teljes pillanatkép).
