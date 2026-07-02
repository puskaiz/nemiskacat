# Shared rich-content body (`nk-rich`) — 2-column layout for blog + pages

- **Dátum:** 2026-07-02
- **Feladat:** a blog-szerkesztőben létező 2 oszlopos elrendezés (kép + képaláírás, `.nk-figure-row`) általánossá tétele, hogy a blogon ÉS a tartalmi oldalakon egyformán renderelődjön.
- **Státusz:** elfogadva (brainstorming)

## Háttér / gyökérok

A 2 oszlopos elrendezés TipTap-node-ja (`FigureRow` → `figureRow`/`figureItem`, kép + opcionális képaláírás), a szerkesztő gomb ("Képoszlopok") és a `BlogHtmlSanitizer` (`nk-figure-row` engedélyezve) **már közös** — az `admin-ui` `HtmlEditor`-t a blog és az oldalak szerkesztője egyaránt használja, így az oldalszerkesztő már be tud szúrni és menteni képoszlopokat.

A hiány kizárólag **CSS-hatókör**: a `site.css`-ben 14 rich-content szabály a `.nk-blog-article__body` prefixhez van kötve (a `.nk-figure-row` család **plusz** az inline képek, kép-párok és táblázatok). A tartalmi oldalak törzse `.nk-page-article__body`, ezért ezeket a stílusokat nem kapják meg — a `.nk-figure-row` markup mentésre kerül, de stílus nélkül (egymás alá) jelenik meg. Nincs csupasz `.nk-blog-article__body {}` szabály; a prefix kizárólag tartalmi hatókörként létezik (14 leszármazott szabály, `site.css` ~1341–1411).

## Döntés: közös tartalmi osztály (`nk-rich`)

Egyetlen közös osztály (`nk-rich`) hordozza a rich-content stílusokat, és mindkét cikk-törzsre rákerül.

1. **`src/main/resources/static/css/site.css`** — a 14 szabály prefixe `.nk-blog-article__body` → `.nk-rich` (érinti: `.nk-figure-row` oszlopok + `figure`/`figcaption`, inline `img`, kép-pár bekezdés, `table`/`th`/`td`/`thead`/`tbody`). A szabálytörzsek változatlanok. A szekció-komment frissül ("Rich content body (blog + pages)").
2. **`src/main/resources/templates/blog/post.html`** — a törzs `div` osztálya `nk-blog-article__body` → `nk-blog-article__body nk-rich` (additív: a régi osztály stabil horgonyként megmarad, a stílust a `nk-rich` adja).
3. **`src/main/resources/templates/page.html`** — a törzs `div` osztálya `nk-page-article__body` → `nk-page-article__body nk-rich`.
4. **Szerkesztő + sanitizer + render-vezérlők** — nincs változás (már közös).

Eredmény: ugyanaz a kép+képaláírás 2 oszlopos elrendezés a blogon és az oldalakon azonosan renderelődik; ráadásul ugyanezzel a mechanizmussal az oldalak megkapják a stílusos képeket és táblázatokat is. Bármely jövőbeli felület (pl. workshop-leírás) a `nk-rich` osztály hozzáadásával csatlakozik.

### Elvetett alternatíva

Minden 14 szelektort `.nk-blog-article__body, .nk-page-article__body`-ra bővíteni: működik, de nem általános (minden új felületet 14 helyen kell felvenni).

## CLAUDE.md-megfelelés

- **#2** A nyilvános oldal-/blogtörzs továbbra is munkamenet-mentes, cache-elhető; csak megjelenítési (CSS/osztály) változás.
- **#10** Kód/komment angol; a tartalom magyar.
- Nincs séma-, API- vagy adatváltozás; a már importált oldalak adatai érintetlenek (render-réteg).

## Tesztelés

- `BlogControllerIT`: a publikált poszt render-tesztje ellenőrzi, hogy a törzs-wrapper tartalmazza a `nk-rich` osztályt.
- `RootSlugControllerIT`: a publikált oldal render-tesztje ellenőrzi ugyanezt a `page.html`-en.
- CSS nem unit-tesztelhető → kézi ellenőrzés: egy blogposzt és egy tartalmi oldal, amely `.nk-figure-row`-t tartalmaz, mindkettőn egymás mellett jelenik meg.

## Scope-on kívül

- Egy oszlop tartalmának általánosítása képen+képaláíráson túl (a döntés: "ugyanaz az elrendezés").
- Oldalszintű tipográfia/térköz-finomítás.
- Admin előnézet-panel hűsége (ma is `site.css` nélkül renderel — a blognál és az oldalaknál egyaránt, ezt nem érinti).
