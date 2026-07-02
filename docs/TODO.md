# TODO — üzleti / adat teendők

Fejlesztéstől független, de élesítés előtt rendezendő tételek. (A fejlesztési
feladatok helye a `docs/TASKS.md`.)

## Terméknév-megjelenítés a termékoldalon (design-bontás) — eldöntendő

A preview a teljes nevet kettébontja: kis script-felirat = márka/termékcsalád
(„Annie Sloan Chalk Paint™"), nagy cím = rövid név („Athenian Black"). Nálunk
egyetlen teljes WooCommerce-név van (pl. „Athenian Black Chalk Paint krétafesték
Annie Sloan"), és a katalógus nevei nagyon eltérőek (festék, pecsét, bútorgomb…),
ezért egy szabály nem bontja szét megbízhatóan. Lehetőségek: (1) heurisztikus
bontás felismert termékcsaládokra + teljes-név fallback; (2) teljes név a címben,
kategória a feliratban (robusztus); (3) külön `display_title` + `brand/range`
mezők importnál/adminban (legpontosabb). **A user átgondolja, később döntünk.**
Jelenleg: teljes név a címben, eyebrow = első kategória.

## Termék-súlyok pótlása (GLS díjsávozáshoz) — Woo-ban szerkesztendő

A GLS szállítási díj súlysáv-alapú; a súly nélküli variánsok 0 kg-nak számítanak,
ami alulszámlázott szállítást okozhat. Az átállásig a termékszerkesztés helye a
WooCommerce — ott pótolandó, az importer áthozza.

Érintett termékek (2026-06-13 állapot, 14 termék):

- [ ] butorfesto-kezdo-csomag-nagy-fa-butorra (53 variáns)
- [ ] laminalt-butor-festocsomag-nagy-butorhoz-ajandek-videoval (53 variáns)
- [ ] chalk-paint-kretafestek-szinminta-fakanalakon (53 variáns)
- [ ] butorfesto-kezdo-csomag-kicsi-fa-butorra (52 variáns)
- [ ] laminalt-butor-festocsomag-kicsi-butorhoz-ajandek-videoval (51 variáns)
- [ ] falfestek-teszter-annie-sloan (32 variáns)
- [ ] fusion-mineral-paint-szinminta-fakanalakon (20 variáns)
- [ ] satin-paint-szinminta-fakanalakon (16 variáns)
- [ ] polyvine-wax-finish-butorlakk (4 variáns)
- [ ] rodmell-chalk-paint-kretafestek-annie-sloan (1 variáns)
- [ ] firle-chalk-paint-kretafestek-annie-sloan (1 variáns)
- [ ] old-white-chalk-paint-kretafestek-annie-sloan (1 variáns)
- [ ] tilton-chalk-paint-kretafestek-annie-sloan (1 variáns)
- [ ] rusztikus-butor-egyszeru-keszitese-mini-videokurzus — **digitális termék**:
  nem súly kell, hanem „nem szállítandó” megjelölés (külön kezelendő a
  checkoutban — l. lentebb)

## Digitális/virtuális termékek kezelése

A videókurzus(ok)nak nincs szállítása. Eldöntendő: virtuális termék flag a
katalógusban + szállításmentes checkout-út. (T9+ scope-kérdés.)

## Blog-import: hiányzó képek pótlása — Woo/WP-ben rendezendő

A WordPress blog-import (2026-06-29, 271 cikk) során 7 inline kép HTTP 404-et
adott: mindegyik a WordPress által generált **`-512x1024.png`** átméretezett
változat, ami a live szerveren hiányzik (az eredeti, teljes méretű feltöltések
nagy valószínűséggel megvannak). Az importer ezeket gráciásan kihagyta, és a
cikk törzsében **meghagyta az eredeti (jelenleg 404-es) WP-URL-t** — addig ott
törött a kép.

Javítás (WP-oldal): a `512x1024` méret újragenerálása a live oldalon (pl.
*Regenerate Thumbnails* plugin), majd **az import újrafuttatása** — idempotens,
csak ezt a 7 képet húzza be és írja át `/media/...`-ra, duplikáció nélkül.
(Alternatíva: ha ezt a méretet szándékosan kivezették, az eredeti — `-512x1024`
suffix nélküli — kép behelyettesítése; ez tartalmi döntés.)

Érintett cikkek (2026-06-29 állapot, 7 publikált cikk, cikkenként 1 kép):

- [ ] `mi-a-kulonbseg-az-annie-sloan-falfestek-es-butorfestek-kozott` — `…/2022/02/Annie-Sloan-falfestek-es-butorfestek-512x1024.png`
- [ ] `ecset-tipusok` — `…/2025/04/ecset-tipusok-festeshez-3-512x1024.png`
- [ ] `kretafestek-hasznalata-falra` — `…/2025/04/kretafestek-hasznalata-falra-512x1024.png`
- [ ] `osszeillo-szinek-lakasban` — `…/2025/05/osszeillo-szinek-a-lakasban-512x1024.png`
- [ ] `vintage-butor-festes` — `…/2025/06/vintage-butor-festes-512x1024.png`
- [ ] `butor-diszites-hazilag` — `…/2025/07/butor-diszites-hazilag-512x1024.png`
- [ ] `lakkozott-butor-felujitasa` — `…/2025/07/lakkozott-butor-felujitasa-a-egyszeruen-512x1024.png`

Közös host mindegyiken: `https://nemiskacat.hu/wp-content/uploads/`.

(A 9 üres-slugú piszkozat — soha nem publikált WIP — szándékosan kimaradt az
importból; nem kép-probléma, nincs teendő.)

## Egyéb

- [ ] ÁSZF és checkout-mikroszövegek (T9 `[EMBER]` bemenete — jogász/üzlet)
- [ ] Kulcs-Soft export/API hozzáférés + D9 ár-törzs döntés (T2/T5 blokkoló)
