# WordPress blog-import — tervdokumentum

- **Dátum:** 2026-06-29
- **Feladat:** a `nemiskacat` WordPress blogcikkeinek átemelése a DB-alapú blog CMS-be (a meglévő termék-/rendelés-/workshop-import mintájára)
- **Branch:** `worktree-blog-import`
- **Előzmény:** a blog CMS motor kész és bemergelt (ADR 0008, `docs/superpowers/specs/2026-06-29-blog-cms-design.md`). Ez a feladat **tartalmat tölt** bele a meglévő `blog_post`/`blog_category` sémába.
- **Státusz:** elfogadva (brainstorming), implementációs terv következik

## 1. Cél és háttér

A `nemiskacat` WordPress helyileg Dockerben fut (`wp_db` konténer, DB `client7002dbnem`, táblaprefix `guxdop_`), az adatbázis közvetlenül elérhető. A meglévő importok bevált, **kétlépéses** mintáját követjük:

1. **Exporter** (Python a `scripts/woo-export/` alatt): `docker exec wp_db mysql …` közvetlenül a WP DB-re, JSON-pillanatképet ír (csak olvas).
2. **Importer** (Java): a JSON-t beolvassa és **idempotensen upsertel** a Postgresbe, a képeket a hash-elt storage-ba tölti.

A blog-import ezt tükrözi pontosan, a `catalog`/`order`/`workshop` importok meglévő osztály- és futtató-mintáira.

### Elfogadott döntések (brainstorming)

- **HTML→Markdown konverzió** importáláskor (flexmark `FlexmarkHtmlConverter`), hogy a tárolt `body_markdown` valódi Markdown legyen és a meglévő render-útvonal (flexmark Markdown→HTML, preview=publikus) **változatlan** maradjon. Egy render-út.
- **Státusz:** WP `publish`→`PUBLISHED` (+`published_at`), `draft`→`DRAFT`. `revision`/`auto-draft`/`trash`/`inherit` kihagyva.
- **Inline képek:** a borítókép ÉS a törzs inline képei is a hash-elt storage-ba töltődnek, az URL-ek `/media/<kulcs>`-ra átírva (túlél a WP kivezetése után).

## 2. CLAUDE.md-megfelelés

- **#4 Idempotencia:** kétszeri futtatásra duplikáció nélkül upsertel; kulcs a **slug** (a WP `post_name`). WP-oldali módosítás a következő futáskor átjön.
- **#7 Slug-megőrzés:** a WP `post_name` 1:1 átvéve, megváltoztatása tilos (SEO).
- **#6 Idő:** `post_date_gmt` UTC-ben tárolva (`published_at`).
- **#8 Képek:** storage-ba hash-elt, megváltoztathatatlan kulccsal; az app képfeldolgozást nem végez.
- **#9 Blog:** a tartalom Postgresben (a DB-alapú CMS-be tölt).
- **#10 Nyelv:** tartalom magyar; kód/commit/komment angol.
- **Tiltások:** a WP-n csak olvasás; éles DB-művelet csak ember; az exporter titkokat nem olvas (csak a blog-táblák).

## 3. Exporter — `scripts/woo-export/export-blog.py`

`export.py` mintájára: `docker exec -i wp_db mysql … -e <query>`, `JSON_OBJECT`-soros lekérdezések, prefix `guxdop_`. Csak a blog-táblákat olvassa.

Olvasott adatok:
- **Posztok** — `{prefix}posts` ahol `post_type='post'` és `post_status IN ('publish','draft')`:
  `ID`, `post_name` (slug), `post_title`, `post_content` (nyers HTML), `post_excerpt`,
  `post_status`, `post_date_gmt`, `post_modified_gmt`.
- **Kategóriák** — `{prefix}terms` + `term_taxonomy` (`taxonomy='category'`) + `term_relationships`:
  kategória `name` + `slug`, és poszt→kategória-slug kapcsolatok. (A `post_tag` taxonómia kihagyva — a modellben csak kezelt kategória van.)
- **Borítókép** — `_thumbnail_id` (postmeta) → attachment poszt → kép-URL (ahogy `export.py` az `image_entries`-ben).
- **SEO** — `_yoast_wpseo_title`, `_yoast_wpseo_metadesc` (postmeta, mint a termékeknél).

Kimenet: egyetlen `blog.json` a `SourceBlog` DTO-alakkal (lásd 4.). Az exporter **képet nem tölt le** és HTML-t nem konvertál — ezt a Java importer végzi. README a `scripts/woo-export/`-ban frissítve.

## 4. Source DTO-k — `integrations/wordpress`

- `SourceBlog(List<SourceBlogPost> posts, List<SourceBlogCategory> categories)`
- `SourceBlogPost(String slug, String title, String contentHtml, String excerpt, String status, OffsetDateTime publishedAt, String coverImageUrl, String seoTitle, String seoDescription, List<String> categorySlugs)`
- `SourceBlogCategory(String name, String slug)`
- `BlogSource` interfész + `JsonFileBlogSource` (Jackson 3, mint `JsonFileCatalogSource`).

## 5. Importer — `application/blog/BlogImporter`

Idempotens, a `CatalogImporter` mintájára; `BlogImportReport`-ot ad vissza (importált / frissített / kihagyott / hibák).

Lépések posztonként:
1. **HTML→Markdown** — `FlexmarkHtmlConverter` (a `flexmark-all` már a pom-ban; megerősítendő, hogy tartalmazza a `flexmark-html2md-converter`-t). A konvertált Markdown lesz a `body_markdown`.
2. **Kép-letöltés a storage-ba** — a borítókép és a törzs inline `<img>`/`![]()` képei a meglévő katalógus-kép-letöltő mechanizmussal (`StorageService.put(bytes, contentType)` → hash-elt kulcs). A törzs Markdown képhivatkozásai `/media/<kulcs>`-ra átírva; a borítókép `cover_image_key`-be. Csak a WP-uploads hosztra mutató képek töltődnek; külső URL-ek érintetlenül maradnak.
3. **Kategóriák** — `blog_category` upsert **slug szerint** (meglévőt nem duplikál), poszt↔kategória összekötés.
4. **Poszt upsert slug szerint** — `BlogPostRepository.findBySlug`; létezőt frissít (cím, excerpt, body, SEO, kategóriák, borítókép, státusz), újat létrehoz. **Az `update`-útvonal a `BlogAdminService`-ben már bevált in-place kollekciókezelést követi** (`getCategories().clear()/addAll()`), hogy a Hibernate dirty-tracking helyes maradjon.
5. **Státusz + `published_at`** — `publish`→`PUBLISHED` és `published_at = post_date_gmt`; `draft`→`DRAFT`.
6. **Ajánlott termékek** — WP-ben nincs → üres; kézi kurálás később.

Idempotencia: a slug-kulcs miatt kétszeri futtatás nem duplikál. Az újrafuttatás a WP aktuális állapotára frissít (cím/törzs/kép). A storage hash-elt kulcsai miatt változatlan kép nem töltődik újra duplán.

## 6. Trigger — `BlogImportRunner` (`@Profile("import")`)

A `CatalogImportRunner` mintájára: az app az `import` profillal és
`--webshop.import.blog-file=<blog.json>` paraméterrel indítva betölti a pillanatképet, logolja a riportot, majd kilép (hibák esetén nem-nulla kóddal). Ütemezés később. **Éles DB-művelet csak ember;** a CI/staging futtathatja.

## 7. Hibakezelés

- Hiányzó/üres slug → poszt kihagyva, riportba (nem áll le az import).
- Letölthetetlen kép → a poszt importálódik kép nélkül (a hivatkozás megmarad vagy kihagyódik), figyelmeztetés a riportba.
- HTML-konverziós hiba egy posztnál → az adott poszt kihagyva, riportba; a többi folytatódik.
- Ismeretlen kategória-slug egy posztnál → a kategória upsertelődik a `categories` listából; ha hiányzik, a poszt kategória nélkül importálódik, figyelmeztetéssel.

## 8. Tesztelés

- **Importer (Testcontainers Postgres):**
  - Idempotencia: ugyanazt a `SourceBlog`-ot kétszer importálva nincs duplikáció (poszt és kategória is).
  - Slug-megőrzés és upsert-frissítés (módosított cím a második futáskor felülírja).
  - HTML→Markdown: címsor/link/lista/`<strong>`/kép helyesen konvertál; az eredmény a `MarkdownRenderer`-rel a várt HTML-t adja (parity).
  - Inline kép: a WP-uploads URL letöltődik a storage-ba és `/media/<kulcs>`-ra íródik át; külső URL érintetlen.
  - Borítókép → `cover_image_key`.
  - Státusz-leképezés + `published_at` (UTC) helyes; draft nem látszik a publikus lekérdezésben (`BlogQueryService`).
  - Kategória-leképezés + poszt↔kategória kapcsolat.
- **Exporter:** minta-fixtúrás Python-teszt (mint `test_export_orders.py`) a lekérdezés→JSON alak helyességére (DB nélkül, fixált `mysql` kimenetre).

## 9. Elfogadási kritérium

- A `scripts/woo-export/export-blog.py` lefuttatva a helyi `wp_db`-re érvényes `blog.json`-t ad a teljes blogról (riport a darabszámról).
- Az app `import` profilban a `blog.json`-t betöltve a cikkek megjelennek az adatbázisban: a publikáltak a `/blog` listán és `/blog/{slug}` oldalon (megőrzött slugokon), Markdown törzzsel, helyi `/media` képekkel, kategóriákkal; a piszkozatok `DRAFT`-ként, publikusan rejtve.
- **Kétszeri import duplikáció nélkül**; a WP-ben módosított cikk a következő importnál frissül.
- Zöld tesztek (importer integráció + exporter egység); futtatható állapot.

## 10. Scope-on kívül (későbbi szeletek)

- WP *tag*-ek átemelése (most csak kategória).
- Ajánlott termékek automatikus társítása importált cikkekhez.
- Szerző/komment-átemelés.
- Inkrementális/ütemezett szinkron (most kézi, teljes pillanatkép).
- Gutenberg-blokkok/shortcode-ok speciális kezelése (a konverzió best-effort; import után kézi átnézés).
