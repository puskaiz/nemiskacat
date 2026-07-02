# Blog page refinements — tervdokumentum

- **Dátum:** 2026-06-30
- **Feladat:** két `/blog` finomítás egy szeletben — (A) mondathatáros kivonat-vágás 30–50 szó között; (B) üres jobb oldali sidebar (1/3) a `/blog` és kategória-oldalon
- **Branch:** `worktree-blog-excerpt-sentence`
- **Előzmény:** a blog CMS + import + lista-redesign + auto-excerpt bemergelt. Ez két kis, független finomítás (backend `ExcerptDeriver` + frontend layout), együtt szállítva.
- **Státusz:** elfogadva (brainstorming), implementációs terv következik

## Feature A — mondathatáros kivonat (`ExcerptDeriver`)

### Cél
A származtatott kivonat lehetőleg **teljes mondattal** záruljon: a 30. szó után az első mondatvégnél vágjon, ha az ≤ 50. szó; egyébként 50 szónál „…"-vel.

### Viselkedés (`derive(String markdown)`)
A flexmark plain-text kinyerés változatlan. Az új vágási logika:
1. Teljes szöveg ≤ `MIN_WORDS` (30) szó → egészben, „…" nélkül.
2. A 30. szótól keresd az **első** olyan szót, amely mondatvégi írásjelre végződik (`.`/`!`/`?`/`…`, záró idéző/zárójel megengedett utána). Ha a **30.–50.** pozícióban van → utána vágj, **„…" NÉLKÜL** (teljes mondat). Több jelölt közül az első (≥30) nyer.
3. Nincs mondatvég a 30–50 ablakban → vágj az **50.** (`MAX_WORDS`) szónál szóhatáron + **„…"**.
4. `null`/üres/whitespace → `""`.
5. A kemény 180-karakteres sapka **megszűnik** (szó/mondat-alapú vágás; ~30–50 szó ≈ 200–350 karakter — a kártyán a CSS ~4 sorra vág, a meta/JSON-LD description-höz elfogadható). Marad egy **biztonsági `MAX_CHARS` = 500** patológiás token ellen (ha emiatt vág, „…").

Konstansok: `MIN_WORDS=30`, `MAX_WORDS=50`, `MAX_CHARS=500` (a korábbi 30/180 lecserélve). Rövidítések („pl.") néha hamis mondatvéget adhatnak — best-effort.

### Hatókör
- Csak `application/blog/ExcerptDeriver` vágási logikája + `ExcerptDeriverTest`. A `BlogQueryService` bekötés változatlan.

## Feature B — üres jobb sidebar (1/3) a `/blog`-on

### Cél
A `/blog` (és `/blog/kategoria/{slug}`) tartalom 2 oszlopra: bal **2/3** a meglévő cikk-lista + lapozó, jobb **1/3** egy **üres** `<aside>` (későbbi widgetek helye). A nemiskacat.hu/blog elrendezését követi.

### Markup (`templates/blog/list.html`)
- A breadcrumb és a „Blog" `title-row` fejléc **teljes szélességben** marad a `<main class="wrap">` tetején (felhasználói döntés).
- Alattuk új 2 oszlopos konténer `.nk-blog-layout`:
  - `.nk-blog-content` (bal, 2/3): a meglévő `.nk-blog-list` (kártyák) + `.pagin` lapozó.
  - `<aside class="nk-blog-sidebar" aria-label="Oldalsáv">` (jobb, 1/3): **üres** (most nincs tartalom).
- Ugyanez a sablon szolgálja a `/blog`-ot és a kategória-oldalt → a sidebar **mindkettőn** megjelenik (felhasználói döntés).

### CSS (`static/css/site.css`)
- `.nk-blog-layout { display: grid; grid-template-columns: 2fr 1fr; gap: var(--nk-space-6); align-items: start; }`
- `.nk-blog-sidebar { /* üres; a rács tartja a 1/3 oszlopot */ }` (most nincs látható stílus; opcionálisan finom elválasztó később).
- **Reszponzív:** `@media (max-width: 900px)` → `grid-template-columns: 1fr` (egy oszlop); az üres sidebar a tartalom alá kerül (mivel üres, nincs látható hatása).
- A `.nk-blog-card` (kártya) belső 50%-os négyzet-kép aránya változatlan; a kártyák most a 2/3 oszlopban élnek (a négyzet ≈ a lap 1/3-a) — elfogadható, vizuálisan ellenőrizzük.

### Hatókör
- `list.html` (a lista + pager 2/3 oszlopba burkolása + üres aside), `site.css` (`.nk-blog-layout`/`.nk-blog-sidebar` + reszponzív). Nincs backend/DTO-változás. A breadcrumb/title-row és a kártya-markup egyébként változatlan.

## CLAUDE.md-megfelelés (mindkét feature)

- **#1/#2** A kivonat-származtatás a service-rétegben; a lista/cikk HTML munkamenet-mentes, cache-elhető marad. **#10** magyar UI / angol kód. Nincs séma-/importer-/DTO-változás; nincs re-import.

## Tesztelés

- **Feature A — `ExcerptDeriverTest`:** több mondatos szöveg → mondatvégen zár (nincs „…") a 30–50 ablakban; hosszú tagolatlan szöveg → 50 szónál „…"; rövid (<30) → egészben; 30 szó előtti mondatvégnél nem vág; `null`/üres → ""; markdown-jelölés eltávolítva.
- **Feature B — `BlogControllerIT`:** `GET /blog` → 200 és a HTML tartalmazza a `nk-blog-layout` + `nk-blog-sidebar` osztályokat (a lista a `.nk-blog-content`-ben); `GET /blog/kategoria/{slug}` → 200 és szintén tartalmazza a sidebart.
- **Vizuális:** headless screenshot a `/blog`-ról — a lista a bal 2/3-ban, jobbra üres 1/3 oszlop; a kivonatok teljes mondattal zárulnak.

## Elfogadási kritérium

- A `/blog` kivonatok lehetőleg teljes mondattal zárulnak (mondathatár 30–50 szó között), egyébként 50 szónál „…".
- A `/blog` és a kategória-oldal 2 oszlopos: bal 2/3 lista + lapozó, jobb 1/3 üres sidebar; szűk kijelzőn egy oszlop.
- Zöld `ExcerptDeriverTest` + `BlogControllerIT` (+ a meglévő blog-tesztek).
