# TASKS.md — Webshop sáv (B)

Sorrendben haladj; egy tétel jellemzően egy munkamenet (a nagyobbak részfeladatokra bonthatók). `[EMBER]` = emberi input/hozzáférés kell előtte. Minden tétel végén az elfogadási kritérium igazolása kötelező.

## T0 — Projektváz és CI
Maven-projekt a CLAUDE.md modulszerkezetével, Flyway, healthcheck, CI (build + tesztek + staging-deploy), `.env`-kezelés. `[EMBER: staging környezet + DB]`
**Elfogadás:** üres app stagingen fut, CI zöld, Flyway baseline-migráció lefut.

## T1 — WooCommerce PoC-export
`scripts/`-szintű exporter a Woo REST API-ra: termékek, variációk, kategóriák, képlisták, SEO-mezők letöltése riporttal (darabszámok, hiányzó SKU-k, variáció-statisztika). Csak olvas! `[EMBER: Woo REST kulcs]`
**Elfogadás:** riport a teljes katalógusról; ismert termékeken szúrópróba hiánytalan.

## T2 — Kulcs-Soft PoC + SKU-párosítás
Kulcs-export beolvasó + kereszt-ellenőrzés a T1 kimenetével: egyértelmű párok, duplikátumok, csak-egyik-oldalon-létezők listája. `[EMBER: Kulcs-Soft export/API + D9 ár-törzs döntés]`
**Elfogadás:** párosítási riport százalékos egyezéssel; az M1-becslés felülvizsgálatához elegendő részletességgel.

## T3 — Katalógus-adatmodell
Entitások + Flyway-migrációk: termék, variáns, kategória, kép-metaadat, árlista; készletállapot-számítás a domain-rétegben (unit-tesztekkel: küszöb, kézi flagek, kifutott).
**Elfogadás:** állapot-szabályok unit-tesztjei zöldek minden átmenetre.

## T4 — Woo-importer (idempotens)
A T1 exporter alapján éles importer: upsert SKU/Woo-ID szerint, képek letöltése a storage-ba hash-elt kulccsal, slug-megőrzés, futási riport. Ütemezhető.
**Elfogadás:** kétszeri futtatás duplikáció nélkül; Woo-oldali módosítás (név, kép, új termék) a következő futásnál átjön.

## T5 — Kulcs-Soft szinkron
Készlet (és D9 szerint ár) szinkron cikkszám-párosítással, eltérés-riporttal; elérhetőségi főkönyv (szinkron − webes rendelések − POS-eladások).
**Elfogadás:** integrációs teszt: szinkron + közbeni rendelés mellett az elérhető mennyiség helyes.

## T6 — Terméklap és kategóriaoldal
Thymeleaf-render a `shared/` design-rendszerrel (A sávból), JSON-LD `Product`, munkamenet-mentes HTML, Cache-Control fejlécek (mikro-cache-re készen).
**Elfogadás:** terméklap cookie nélkül byte-azonos két látogatónak; JSON-LD validál; elfogyott termék állapota helyesen jelenik meg.

## T7 — Meilisearch termékindex
Termékindexelés az A sáv `docs/search-schema.md` sémája szerint, változás-vezérelt frissítéssel.
**Elfogadás:** termékváltozás után az index frissül; a közös keresőfelület termék-találatot ad.

## T8 — Kosár és session
Guest-cart cookie-azonosítóval, kosár-API (a statikus oldalak szigete is ezt hívja), `/api/session` (no-store), belépéskori kosár-összefésülés, készletállapot-ellenőrzés kosárba tételkor. CSRF-védelem.
**Elfogadás:** statikus oldalról kosárba tett termék megjelenik a webshop-kosárban; session-végpont kosár-cookie nélkül olcsó/kihagyható.

## T9 — Checkout váz + díjszámítás
Checkout-folyamat (cím, szállítási mód, díjtábla konfigurációból, áfa), rendelés-domain állapotgéppel, idempotens rendelésrögzítés (kliens-kulcs), készlet-újraellenőrzés checkout-indításkor, egydarabos termékek kapcsolható 15 perces foglalása. `[EMBER: díjtábla + ÁSZF-szövegek]`
**Elfogadás:** unit/integrációs tesztek a díj- és áfa-számításra, a foglalás-lejáratra és a dupla-rögzítés elleni védelemre.

## T10 — KHPos-fizetés + hibaágak
Starter bekötése: indítás, visszatérés, callback-feldolgozás újrafeldolgozás-biztosan; időzített újraellenőrzés elmaradt callbackre; „fizetve, de rögzítés hibázott" riasztással. `[EMBER: KHPos staging-kulcsok]`
**Elfogadás:** integrációs tesztek minden hibaágra (megszakítás, késő callback, dupla callback); staging tesztfizetés végigmegy.

## T11 — Számla + futár
szamlazz.hu számla rendelés-fizetéskor, sztornószámla a visszáru-folyamatban; MyGLS/Kvikk címke + tracking-visszaírás. `[EMBER: staging/teszt fiókok]`
**Elfogadás:** staging-rendeléshez számla és címke készül; sztornó-út tesztelt.

## T12 — Tranzakciós e-mailek
Sablonok (visszaigazolás, fizetés, feladás trackinggel, jelszó-reset), sorba állított küldés retry-jal, kézbesítési napló. `[EMBER: e-mail szolgáltató + domain-hitelesítés (SPF/DKIM/DMARC) + sablonszövegek]`
**Elfogadás:** staging-rendelés minden e-mailje kimegy és naplózódik; sablon-előnézet végpont működik.

## T13 — Vásárlói fiók
Spring Security: regisztráció, belépés, jelszó-reset, címek, rendeléstörténet, GDPR-export és fióktörlés.
**Elfogadás:** E2E: regisztráció → rendelés → rendeléstörténet; törlés után személyes adat nem marad (rendelés anonimizálva).

## T14 — Playwright E2E a checkoutra
Teljes vásárlói út (statikus oldali kosárba tételtől a fizetés-visszatérésig) a CI-ban, vendég és belépett módban.
**Elfogadás:** E2E zöld a CI-ban; szándékos hibainjektálásnál (callback-késés) az út a definiált hibaképernyőre fut.

## T15 — Admin API + Refine alap
OpenAPI-ból generált TS-kliens, Refine-projekt, szerepkörös belépés, audit log az ár- és rendelésmódosításokra.
**Elfogadás:** admin belép, rendeléslistát lát szűrőkkel; minden módosítás auditnaplóba kerül.

## T16 — Admin rendeléskezelés
Rendeléslap, státuszgép (új → fizetve → csomagolás → feladva → teljesítve), sztornó + sztornószámla, visszáru/részvisszatérítés, címkenyomtatás. Scope: csak a napi folyamat (CLAUDE.md).
**Elfogadás:** próbarendelés teljes életciklusa az adminból, fejlesztői beavatkozás nélkül.

## T17 — Admin katalóguskezelés
Termék- és képszerkesztés (feature flag mögött az átállásig!), kategóriakezelés + webhook a statikus build felé, kupon-adminisztráció.
**Elfogadás:** kategória-mentés triggereli a statikus rebuildet (K5); a termékszerkesztés flag nélkül nem elérhető.

## T18 — Kupon-motor (alap)
Kuponkód, %/fix kedvezmény, érvényesség, min. kosárérték, felhasználás-számlálás — domain-szabályok unit-tesztekkel. `[EMBER: kuponszabályok definíciója]`
**Elfogadás:** szabály-tesztmátrix zöld; checkout helyes végösszeget számol kuponnal.

## T-BLOG — Blog a webshopban
`content/blog/` Markdown-render (flexmark) Thymeleaf-sablonba a `shared/` designnal; front matter: meta + ajánlott termékek (kategória/SKU); ajánló-doboz elérhetőségi állapottal, elfogyott/kifutott automatikus kihagyással; publikálás merge → deploy → érintett URL purge (fordított hivatkozási lista alapján termék-állapotváltáskor is); `Article` JSON-LD; bloglista a régi lapozó-URL-eken; AI-cikksablon + szerkesztői útmutató (`docs/blog-workflow.md`); blog-indexelés a Meilisearch közös sémájába. `[EMBER: konvertált blogtartalom az A sáv T2-jéből]`
**Elfogadás:** próbacikk PR-ból élesedik stagingen ajánlott termékekkel; elfogyott termék kikerül az ajánlóból állapotváltás után.

> **Állapot (2026-06-29):** Az első szelet — DB-alapú CMS (cikkek, kezelt kategóriák, ajánlódoboz, admin szerkesztő, nyilvános render) — átadva a `worktree-blog-cms` ágon (ADR 0008). A fájlalapú `content/blog/` forrás ki van vezetve. Fennmaradó tételek külön követve: Meilisearch-indexelés, cache purge / fordított hivatkozási lista, régi lapozó-URL-ek, ütemezett publikálás, AI-cikksablon.

## T-IG — Instagram-feed a sávban (saját fiók)
Szerveroldali Instagram-adapter az `integrations/instagram` alatt (a Kulcs-Soft adapter mintájára), Spring `RestClient`-tel a Graph API-ra (v22.0, `instagram_basic`, Standard Access — kizárólag a bolt saját fiókja, így App Review nélkül). A hosszú élettartamú access token titokként tárolva (env/secret store, sosem a repóba); ütemezett token-frissítő job a ~60 napos lejárat előtt, riasztással hibára. Ütemezett lekérés a legutóbbi N posztra szerveroldali cache-be; a nyilvános oldal a *saját* másolatból renderel (munkamenet-mentes, cache-elhető HTML — #2 szabály), Thymeleaf-sablonsziget, böngészős Instagram-JS nélkül (#3 szabály); a CSP csak az Instagram kép-CDN origint engedi (vagy a thumbnail proxyzva/cache-elve #8 szerint). Hibatűrés: holt token / sikertelen lekérés esetén az utolsó jó cache vagy elrejtés — sosem töri/blokkolja az oldalt. Elvetett opciók és indoklás: ADR 0010 (instagram4j = privát API/ToS-sértés; harmadik féltől származó JS-widget = islands-kivétel; RestFB = felesleges függőség). `[EMBER: a bolt Instagram-fiók Business/Creator-ré alakítása + Facebook-oldalhoz kötése, Meta-app létrehozása, fiók szerepkör az appban, egyszeri OAuth a hosszú élettartamú tokenhez]`
**Elfogadás:** integrációs teszt a lekérés→cache→render útra (mockolt Graph API); a token-frissítő job lefutása és hiba-riasztása tesztelt; a nyilvános oldal cookie nélkül byte-azonos, és holt token mellett is hibátlanul renderel (utolsó cache / elrejtés).

> **Állapot (2026-07-01):** Kész a `worktree-instagram-feed` ágon (mergre kész, 452 teszt zöld). A háttér elkészült: `integrations/instagram` (RestClient-adapter fetch+token-frissítés, https-séma-ellenőrzés, timeoutok), DB-cache (`V25__instagram_feed.sql`: `instagram_media` + `instagram_token`), atomi cache-csere külön `@Transactional` beanben, ütemezett szinkron, `InstagramFeedQuery` port + letiltott stub, `instagram.enabled` flag (alapból ki). **Renderelés-változás (l. ADR 0010 addendum):** mivel a main közben leszállította a blog-oldalsáv blokk-CMS-t, a feed nem külön sziget, hanem `INSTAGRAM` blokktípus a `SidebarQueryService`/`fragments/blog.html`-ben, a posztokat élőben az `InstagramFeedQuery`-ből húzva (`V26__instagram_sidebar_block.sql` seed). Üres/letiltott feed → nincs blokk. Nyitott: (1) `[EMBER]` Meta-fiók/app + token az élesítéshez (`INSTAGRAM_ENABLED=true` + env); (2) az Instagram-blokk admin-UI szerkesztése az oldalsáv-CMS Phase 2-jében; (3) CSP `img-src` engedélylista az Instagram-CDN-re, ha bevezetünk CSP-t.

## T19 — Feedek
Közös feed-generátor → Google Merchant XML + Meta-katalógus, `availability` az állapotmodellből, naponta többszöri frissítés.
**Elfogadás:** a feed validátor-hiba nélkül betölt a Merchant Centerbe (teszt-fiók); ár/állapot egyezik a terméklap JSON-LD-jével.

## T20 — Mérés és consent
GA4 e-commerce + Meta Pixel kliensoldalon; szerveroldali Meta CAPI + GA4 Measurement Protocol esemény-deduplikációval; consent-kezelő + Consent Mode v2 a közös JS-szigetben (A sávval egyeztetve). `[EMBER: GA4/Meta fiókok, consent-szövegek]`
**Elfogadás:** tesztrendelés dedupláltan jelenik meg GA4-ben és a Meta Events Managerben; consent-elutasításnál nem megy ki marketing-esemény.

## T21 — Marketingplatform-események
Feliratkozás, rendelés, kosárelhagyás, „értesítsen, ha elérhető" események a kiválasztott platform API-jára, adapter-interfész mögött. `[EMBER: D4 platformdöntés + fiók]`
**Elfogadás:** mind a négy esemény megjelenik a platformon staging-tesztben.

## T22 — Cutover-támogatás
Végső delta-import parancs, Woo írásvédetté tételi checklist, termékszerkesztő-flag átkapcsolás, `docs/golive-checklist.md` (soft launch forgatókönyv, visszaállási terv).
**Elfogadás:** a checklist alapján a soft launch és az éles átállás lépésről lépésre követhető; a visszaállási út (DNS/routing vissza) dokumentált és kipróbált stagingen.

## T23 — Üzemeltetési alapok
Monitoring + riasztás, Sentry a checkout-frontendre, napi DB-mentés + visszaállítási próba dokumentálva, rate limit a publikus API-n.
**Elfogadás:** szimulált hiba riasztást vált ki; mentés-visszaállítás jegyzőkönyv készült.
