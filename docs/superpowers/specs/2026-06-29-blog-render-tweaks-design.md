# Blog rendering tweaks — tervdokumentum

- **Dátum:** 2026-06-29
- **Feladat:** három front-end finomítás a DB-alapú blog megjelenítésén (a WP-vel való egyezés + galéria-elrendezés)
- **Branch:** `worktree-blog-render`
- **Előzmény:** a blog CMS + a WordPress-import kész és bemergelt (ADR 0008). Ez kizárólag a **megjelenítést** érinti — nincs séma-, importer- vagy adatmodell-változás.
- **Státusz:** elfogadva (brainstorming), implementációs terv következik

## 1. Cél

Három, egymástól független megjelenítési igazítás:

1. **Cikk-URL a gyökérre:** a WP `https://nemiskacat.hu/ombre-butorfestes/` sémát kövessük — nálunk a cikk `/{slug}` legyen (jelenleg `/blog/{slug}`).
2. **Borítókép elrejtése a cikkoldalon:** a cikk tetején ne jelenjen meg a nagy borítókép (hero).
3. **Side-by-side képek:** a WP-n egymás mellett megjelenő képcsoportok nálunk is egy sorban jelenjenek meg.

## 2. CLAUDE.md-megfelelés

- **#2** Munkamenet-mentes, cache-elhető HTML marad (csak template/CSS és route változik; nincs felhasználó-/kosárfüggő adat).
- **#7** Slug 1:1 a WP-ből; a `/{slug}` gyökér-URL pontosan a WP-kanonikus forma.
- **#1** Nincs üzleti logika a controllerben (a `BlogController` vékony marad).
- **#10** Tartalom magyar; kód/commit/komment angol.

## 3. Hatókör

### Benne
- `BlogController` cikk-route gyökérre + a belső linkek és canonical/og:url igazítása.
- Borítókép `<img>` eltávolítása a cikk-sablonból.
- Új blog-cikk CSS (galéria-sor + kép-szabályok) a fő stíluslapba.

### Kívül (nem most)
- Re-import vagy adat-/HTML-konverzió módosítása (a galéria-csoportosítás már megvan az adatban).
- A lista (`/blog`) és kategória (`/blog/kategoria/{slug}`) URL-ek mozgatása — maradnak.
- Régi `/blog/{slug}` → `/{slug}` átirányítás: a tartalom publikusan még nem élt ezen az URL-en (átállás előtt), ezért nincs szükség redirектre; a `/blog/{slug}` egyszerűen 404 lesz.
- A gyökér `/{slug}` route mostantól elnyeli a jövőbeli gyökérszintű statikus fájlokat (pl. `/robots.txt`, `/favicon.ico`, `/sitemap.xml`) — jelenleg nincs ilyen (`static/` üres); egy jövőbeli SEO-feladatnak ezt figyelembe kell vennie.

## 4. Cikk-URL a gyökérre (`/{slug}`)

- `web/BlogController`:
  - A cikk-mapping `@GetMapping({"/blog/{slug}", "/blog/{slug}/"})` → `@GetMapping({"/{slug}", "/{slug}/"})`. A handler logikája változatlan (slug → `getPublishedBySlug` → 404 ha üres).
  - A lista (`@GetMapping("/blog")`) és kategória (`@GetMapping({"/blog/kategoria/{slug}", "/blog/kategoria/{slug}/"})`) **változatlan**.
- **Route-precedencia / ütközés:** Spring a literális/specifikusabb mintát illeszti előbb. A `/`, `/blog`, `/cart`, `/penztar/...`, `/product/...`, `/termekkategoria/...`, `/fiokom/...` mind elnyerik a `/{slug}` egy-szegmensű mintát; a kétszegmensű `/blog/kategoria/...` szintén nem ütközik. Ismeretlen egy-szegmensű útvonal → `getPublishedBySlug` üres → 404 (a `BlogController` már most ezt teszi). A `/{slug}` lényegében a nem illeszkedő egy-szegmensű utak de-facto 404-ágává válik — elfogadható.
- `templates/blog/list.html`: a két cikk-link `@{'/blog/' + ${item.slug}}` → `@{'/' + ${item.slug}}` (kép-link és cím-link). A kategória-linkek és a lapozó URL-ek változatlanok.
- `templates/blog/post.html`: a cikkben lévő kategória-linkek maradnak `/blog/kategoria/...`.
- **SEO / canonical:** nem adunk per-oldal `canonical`/`og:url` taget. Indok: a `<head>` közös `th:replace` fragment (az egész head cseréje), és a projekt bevált mintája — a SEO-kritikus **terméklap** — sem tesz per-oldal canonicalt (a JSON-LD a body végén van). Per-oldal canonical bevezetése a megosztott head-fragment módosítását igényelné (minden oldalt érint) → hatókörön kívül. A cikk slug-ja (`/{slug}`) eleve a WP-kanonikus forma.

## 5. Borítókép elrejtése a cikkoldalon

- `templates/blog/post.html`: a `<img class="nk-blog-article__cover" …>` elem **törlése** (a `th:if="${post.coverImageUrl}"` blokk).
- **Megmarad:** a borítókép a lista-kártyán (`list.html` változatlan) és az `Article` JSON-LD `image` mezőjében (a `BlogQueryService`/`jsonLd` változatlan). Csak a cikkoldali nagy hero tűnik el.
- Nincs backend-változás.

## 6. Side-by-side képek (CSS)

A blog-cikk törzsének **jelenleg nincs dedikált CSS-e** (a `nk-blog-article__*` osztályokra nincs szabály a repóban) — ezért blog-cikk stílusokat **hozzáadunk** a fő stíluslaphoz (amit a `fragments/layout :: head` tölt be; a pontos fájlt a terv rögzíti).

A galéria-csoportosítás már az adatban van: a HTML→Markdown konverzió a WP-galériát **egy bekezdésben több képként** hozta át (pl. `ombre-butorfestes`: 4 kép egy Markdown-sorban → flexmark egy `<p>`-be több inline `<img>`-et renderel), míg az önálló képek külön bekezdésben (egy `<p>` egy `<img>`).

CSS-szabályok a `.nk-blog-article__body` alatt:
- **Önálló kép** (`p` egyetlen `<img>`-gel): blokk, `max-width: 100%`, középre, lekerekített sarok/space, mint egy normál cikk-kép.
- **Galéria-sor** (`p` több `<img>`-gel — szelektor: `.nk-blog-article__body p:has(img + img)`): `display: flex; flex-wrap: wrap; gap`. A gyermek `<img>`-ek `flex: 1 1 <bázis>` úgy, hogy asztali gépen egy sorban legyenek, **szűk kijelzőn 2 oszlopba tördeljenek** (reszponzív rács, nincs vízszintes görgetés).
- A `:has()` szelektor a jelenlegi böngészőkben támogatott; ha a célböngésző-mátrix miatt kockázatos, a terv fallbackként a galéria-bekezdéseket szerver-oldalon megjelölheti — de elsőre tiszta CSS, backend nélkül.

Tisztán front-end; nincs adat-, importer- vagy DTO-változás; nincs újraimport.

## 7. Tesztelés

- **Web (MockMvc + Testcontainers, a meglévő `BlogControllerIT` mintájára):**
  - `GET /{slug}` (publikált cikk) → 200, tartalmaz `"@type":"Article"`-t és a cikk `<h1>`/törzsét.
  - `GET /{slug}` ismeretlen slugra → 404 (nem nyel el más oldalt).
  - `GET /blog` → 200; `GET /blog/kategoria/{slug}` → 200 (változatlanul működnek).
  - A cikkoldal HTML-je **nem** tartalmaz `nk-blog-article__cover` képet.
  - Galéria: egy több-képes bekezdést tartalmazó cikknél a törzs egy `<p>`-ben több `<img>`-et renderel (a struktúra ellenőrizhető; a tényleges elrendezés vizuális).
- **Vizuális ellenőrzés** (futó app / böngésző): a `/ombre-butorfestes` oldalon a 4 kép egy sorban, borítókép nélkül; mobilszélességen 2 oszlopba tördel.

## 8. Elfogadási kritérium

- `http://localhost:8085/ombre-butorfestes` betölti a cikket (a `/blog` prefix nélkül); a `/blog/ombre-butorfestes` 404.
- A cikk tetején nincs nagy borítókép; a lista-kártyákon és a JSON-LD-ben megmarad.
- Az `ombre-butorfestes` négyes képcsoportja egy sorban jelenik meg asztali nézetben, mobilon tördelve.
- A `/blog` lista és a kategória-oldalak változatlanul működnek; zöld web-tesztek.
