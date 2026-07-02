# Blog list page redesign — tervdokumentum

- **Dátum:** 2026-06-30
- **Feladat:** a `/blog` lista (és kategória-lista) átalakítása vízszintes sor-elrendezésre — négyzetes kép balra (kb. fél szélesség), cím + rövid leírás + „Tovább olvasom" jobbra — a nemiskacat.hu/blog alsó listája mintájára
- **Branch:** `worktree-blog-list-design`
- **Előzmény:** a blog CMS + WP-import + render-finomítások kész és bemergelt. A lista-kártyáknak (`nk-blog-card`) jelenleg NINCS CSS-e (alap blokk-elrendezés). Ez front-end (template + CSS) feladat.
- **Státusz:** elfogadva (brainstorming), implementációs terv következik

## 1. Cél

A `/blog` és `/blog/kategoria/{slug}` listaoldal kártyái a referencia (nemiskacat.hu/blog alsó lista) vízszintes sor-elrendezését kövessék: minden cikk egy sor, balra négyzetes borítókép (a lap kb. fele), a képre úsztatott kategória-címkével; jobbra a cím, kis dátum, rövid leírás és egy „Tovább olvasom »" link.

## 2. CLAUDE.md-megfelelés

- **#2** Munkamenet-mentes, cache-elhető HTML (csak template + CSS; nincs felhasználó-/kosárfüggő adat).
- **#1** Nincs üzleti logika a controllerben (csak a meglévő `BlogQueryService` listaadatát rendereljük).
- **#10** Felület magyar; kód/komment/commit angol.
- Nincs séma-, importer-, DTO- vagy backend-változás.

## 3. Hatókör

### Benne
- `templates/blog/list.html`: a kártya markup átrendezése (kategória a képre úsztatva; „Tovább olvasom »" link; borítókép-placeholder, ha nincs kép).
- `static/css/site.css`: új `.nk-blog-card*` szabályok (vízszintes sor, négyzetes kép, overlay-címke, reszponzív tördelés). A design-tokeneket használja.

### Kívül (nem most)
- Backend/DTO-változás (a `BlogListItem` minden szükséges mezőt ad: `slug, title, excerpt, coverImageUrl, publishedDate, categories`).
- „Load more"/végtelen görgetés: marad a meglévő számozott lapozó (`.pagin`).
- Külön „kiemelt" rács a lista tetején (a referencia tetején van; mi a sor-listát csináljuk).
- A kategória-oldal és a lapozó URL-logikája változatlan.

## 4. Kártya-elrendezés (`.nk-blog-card`)

Vízszintes 2 oszlopos sor, soronként egy cikk:

- **Bal oszlop (~50%) — `.nk-blog-card__image-link`** (a teljes kép a cikkre linkel, `/{slug}`):
  - négyzetes: `aspect-ratio: 1 / 1`, a kép `object-fit: cover` (a nem-négyzetes források középre vágva); lekerekített sarok a `--nk-radius-*` tokennel.
  - **kategória-overlay — `.nk-blog-card__cats`**: a kép bal felső sarkára abszolút pozícionált, kis pill-címke(k); minden címke a `/blog/kategoria/{slug}`-ra linkel.
  - ha `coverImageUrl == null`: semleges placeholder tölti ki a négyzetet (a sor igazítása megmarad).
- **Jobb oszlop (~50%) — `.nk-blog-card__body`** (függőleges flex):
  - `.nk-blog-card__title` (h2, a cikkre linkel),
  - `.nk-blog-card__date` (kicsi, halvány; `yyyy. MM. dd.`, csak ha van dátum),
  - `.nk-blog-card__excerpt` (rövid leírás; `-webkit-line-clamp` ~3–4 sorra vágva, hogy a sorok egyenletesek legyenek; csak ha van excerpt),
  - `.nk-blog-card__readmore` — „Tovább olvasom »" link a `/{slug}`-ra.
- **Lista-konténer — `.nk-blog-list`** (a jelenlegi `.nk-blog-grid` helyett): függőleges flex/blokk, sorok közti `gap`. A `<main class="wrap">`-en belül (teljes szélesség; a négyzet a szélesség fele).
- **Reszponzív:** `@media (max-width: 640px)` — a kártya egymás alá tördel: kép felül (teljes szélesség, négyzet), szöveg alatta.

## 5. Markup-változások (`list.html`)

- A jelenlegi `.nk-blog-grid` → `.nk-blog-list`.
- A `.nk-blog-card__cats` blokk a `.nk-blog-card__image-link`-en belülre (overlay) kerül a body helyett.
- A `.nk-blog-card__image-link`: ha nincs kép, `<img>` helyett `.nk-blog-card__image--placeholder` div.
- A body végére: `<a class="nk-blog-card__readmore" th:href="@{'/' + ${item.slug}}">Tovább olvasom »</a>`.
- A breadcrumb, a „Blog" fejléc (`title-row`) és a `.pagin` lapozó változatlan.

## 6. Tesztelés

- **Web (MockMvc, a meglévő `BlogControllerIT` mintájára):** `GET /blog` → 200; a lista HTML tartalmazza a `nk-blog-card`/`nk-blog-list` osztályokat, a „Tovább olvasom" linket, és a cikk-link `/{slug}` (nem `/blog/{slug}`). Kategória-oldal (`/blog/kategoria/{slug}`) → 200 és ugyanazt a sablont rendereli. Ezek struktúra-ellenőrzések; a tényleges vizuális elrendezés vizuális.
- **Vizuális ellenőrzés (headless screenshot a futó appról):** a `/blog` sorai négyzetes-kép-balra / szöveg-jobbra elrendezést mutatnak, a képen kategória-címkével, a leírás alatt „Tovább olvasom" linkkel; mobilszélességen egymás alá tördel. Itt finomítható a fél-szélesség arány, ha túl nagy.

## 7. Elfogadási kritérium

- A `/blog` (és `/blog/kategoria/{slug}`) minden cikke vízszintes sorként jelenik meg: balra négyzetes borítókép (a lap kb. fele) a képre úsztatott kategória-címkével, jobbra cím + kis dátum + rövid leírás + „Tovább olvasom »".
- Kép nélküli cikknél placeholder tartja a négyzetet; az elrendezés nem törik.
- Mobilszélességen a kép a szöveg fölé tördel.
- A lapozó és a kategória-oldal változatlanul működik; zöld web-tesztek.
