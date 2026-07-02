# Blog auto-excerpt fallback — tervdokumentum

- **Dátum:** 2026-06-30
- **Feladat:** ha egy blogcikknek nincs kézi `excerpt`-je, származtassunk rövid leírást a cikk törzséből — hogy a `/blog` lista-kártyákon (és az Article/meta description-ben) megjelenjen a rövid leírás, a nemiskacat.hu/blog mintájára
- **Branch:** `worktree-blog-excerpt`
- **Háttér:** a WordPress-import mind a 263 publikált cikknél üres `post_excerpt`-et hozott (a `BlogListItem.excerpt = p.getExcerpt()` null), ezért a lista-kártya feltételes leírás-blokkja semmit sem renderel. A WordPress automatikusan generál kivonatot a tartalomból, ha nincs kézi — ezt pótoljuk.
- **Státusz:** elfogadva (brainstorming), implementációs terv következik

## 1. Cél

Származtatott rövid leírás a cikk törzséből (Markdown), ha nincs kézi `excerpt`. Megjelenik a `/blog` (és `/blog/kategoria/{slug}`) lista-kártyáin, és kitölti az article-oldali meta/`Article` JSON-LD `description` fallbacket. Kézi `excerpt` mindig nyer.

## 2. CLAUDE.md-megfelelés

- **#1** A származtatás a service-rétegben (`BlogQueryService`) történik; a controller vékony marad.
- **#2** A lista- és cikkoldal HTML-je munkamenet-mentes, cache-elhető marad (csak származtatott szövegmező; nincs felhasználó-/kosárfüggő adat).
- **#10** Felület magyar; kód/komment/commit angol.
- Nincs séma-, importer- vagy DTO-mező-változás; nincs re-import (lekérdezéskor származtatott).

## 3. Hatókör

### Benne
- Új `application/blog/ExcerptDeriver` komponens: Markdown → sima szöveges rövid kivonat.
- `BlogQueryService`: `effectiveExcerpt(BlogPost)` helper + bekötés a lista-kivonatba és az article description-fallbackbe.
- Unit + integrációs tesztek.

### Kívül (nem most)
- DB-mező vagy importer módosítása (a kézi `excerpt` mező marad, csak a megjelenítés-idejű fallback új).
- Re-import.
- A `/blog` kártya-elrendezés (kész) vagy egyéb blog-oldal változtatása.

## 4. `ExcerptDeriver` (`application/blog`)

`@Component`, egyetlen metódus:

```
String derive(String markdown)
```

- `null`/üres/whitespace bemenet → `""`.
- A Markdownt **sima szöveggé** alakítja: a flexmark parserrel (amit a projekt már használ) elemzi, és a `com.vladsch.flexmark.ast` `TextCollectingVisitor`-ral kinyeri a szöveget (a címsor/félkövér/link/lista jelölést és a képeket figyelmen kívül hagyja).
- Whitespace összevonása (több szóköz/újsor → egy szóköz), trim.
- Az első **~30 szó**, **~180 karakteres** felső korláttal, **szóhatáron** vágva; ha vágás történt, `…` hozzáfűzve.
- Tiszta, izoláltan tesztelhető egység; nincs Spring-/DB-függése a flexmarkon kívül.

## 5. Bekötés — `BlogQueryService`

- Új privát helper:
  ```
  private String effectiveExcerpt(BlogPost p) {
      return (p.getExcerpt() != null && !p.getExcerpt().isBlank())
          ? p.getExcerpt()
          : excerptDeriver.derive(p.getBodyMarkdown());
  }
  ```
  (`null`/üres derivált → `null` a mezőben, hogy a sablon `th:if`-je rejtse — vagy üres string; a sablon `th:if` mindkettőt rejti.)
- `toListView`: a `BlogListItem` `excerpt`-je `p.getExcerpt()` helyett `effectiveExcerpt(p)`.
- `toPostView`: a `resolvedDescription` fallback `p.getExcerpt()` helyett `effectiveExcerpt(p)` (így a meta description és az `Article` JSON-LD `description` is kitöltődik, ha van kézi seoDescription, az nyer).
- Az `ExcerptDeriver` konstruktor-injektálva (a `@RequiredArgsConstructor` mintára).

## 6. Tesztelés

- **`ExcerptDeriverTest` (unit, nincs Spring/DB):**
  - címsor/félkövör/link/lista/kép tartalmú Markdown → tiszta szöveg, jelölés nélkül; az első ~30 szó + `…`, ha hosszabb;
  - rövid (<30 szó) body → nincs `…`;
  - `null`/üres/whitespace → `""`;
  - kép-only sor (`![](...)`) nem szennyezi a kivonatot.
- **`BlogQueryServiceIT` (Testcontainers):**
  - kézi `excerpt` nélküli publikált cikk (van body) → a `publishedList` item `excerpt`-je nem üres és a body szövegével kezdődik;
  - kézi `excerpt`-tel rendelkező cikk → a lista a kézi `excerpt`-et adja (nem a derivált);
  - (opcionális) a `getPublishedBySlug` view `seoDescription`-je kézi excerpt nélkül a deriváltat adja.
- **Vizuális:** headless screenshot a `/blog`-ról — a címek alatt megjelenik a rövid leírás.

## 7. Elfogadási kritérium

- A `/blog` lista minden cikkénél megjelenik egy rövid leírás a cím alatt (kézi `excerpt` hiányában a body első ~30 szavából, `…`-vel), a Markdown-jelölés nélkül.
- Kézi `excerpt` esetén az jelenik meg.
- Az article-oldal meta/`Article` JSON-LD `description`-je kitöltött (kézi seoDescription vagy a derivált).
- Zöld unit + integrációs tesztek.
