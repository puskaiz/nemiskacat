# Munkacsomag B — Webshop fejlesztés (Spring Boot)

**Cél:** az MVP-webshop élesítése és a WordPress lekapcsolása — **a blog renderelésével együtt** (a cikkek Markdown-forrását az A sáv konvertálja, a webshop rendereli őket ajánlott termékekkel). Becsült terjedelem az élesítésig: **~98–152 embernap** (a statikus sáv nélkül), két fejlesztővel 4–6 hónap. A sáv a statikus munkacsomaggal párhuzamosan fut; az érintkezési pontokat a K1–K5 jelek mutatják (ld. A munkacsomag 3. fejezete).

---

## 1. Induláshoz szükséges döntések és beszerzések (blokkolók)

| # | Döntés / teendő | Javaslat | Mikorra |
|---|---|---|---|
| D1 | Adatbázis | PostgreSQL | kickoff |
| D2 | Hosting (app + DB) | hazai VPS vagy EU-felhő, staging + éles környezet | Sprint 0 |
| D3 | Admin SPA keretrendszer | Refine vagy React-Admin — fél napos spike mindkettőre, utána döntés | Sprint 0 |
| D4 | Marketingplatform | Brevo / Klaviyo / SalesAutopilot-megtartás — a 9. modulig elég | élesítés −1 hónap |
| D5 | Kulcs-Soft hozzáférés | export/API dokumentáció + tesztadat a PoC-hoz | **kickoff előtt** |
| D8 | WooCommerce REST API kulcs | termék-, variáció-, kategória-export a PoC-hoz és az importerhez | **kickoff előtt** |
| D9 | Ár-törzs tisztázása | melyik rendszer az ár forrása (feltételezés: Kulcs-Soft), és hol élnek az akciós árak | kickoff |
| D6 | Tranzakciós e-mail szolgáltató | SES / Mailgun / Brevo SMTP; domain-hitelesítés (SPF, DKIM, DMARC) | 7. modul előtt |
| D7 | Futnak-e Google/Meta hirdetések élesítéskor? | ha igen: a 9. modul az élesítés feltétele | élesítés −1 hónap |

## 2. Sprint 0 — Alapozás (2–3 hét)

- **Projektváz:** moduláris monolit — `domain` (entitások + üzleti szabályok), `application` (service-réteg), `web` (Thymeleaf-controllerek), `api` (REST-controllerek), `integrations` (starterek + adapterek), `admin-ui` (külön repo vagy mappa az SPA-nak).
- **Konvenciók az első naptól:** Flyway DB-migrációk; OpenAPI-leírás a REST-rétegre (springdoc) + generált TypeScript-kliens; problem+json hibaformátum; HttpOnly session + CSRF; minden Thymeleaf-oldal munkamenet-mentes, kivéve kosár/checkout/fiók.
- **CI/CD:** build + tesztek + staging-deploy automatikusan; éles deploy gombra.
- **Adatforrás-PoC (kiemelt!):** a két forrás kereszt-ellenőrzése. (1) WooCommerce REST API: termékek, variációk, kategóriák, képek, SEO-mezők próba-exportja. (2) Kulcs-Soft: export/API mezőtérkép (cikkszám, ár, készlet). (3) **SKU-párosítás:** hány termék párosítható egyértelműen cikkszám alapján a két rendszer között — duplikátumok, hiányzók, eltérő írásmódok listája. **A PoC kiértékelése után frissítjük az M1 becslését.**
- A `shared/` fragment átvétele a statikus sávból (K1), kép-storage konvenciók átvétele (K3).

*Mérföldkő:* üres, de deployolt alkalmazás stagingen, egészség-végponttal, CI-val; Kulcs-Soft PoC jegyzőkönyv.

## 3. Modulok, sorrendben

A sorrend a függőségeket követi; minden modul végén demózható állapot van.

### M1. Termékkatalógus: WooCommerce-import + Kulcs-Soft szinkron (15–25 nap)

**A katalógus elsődleges forrása a WooCommerce** — a nevek, leírások, képek, kategóriák és SEO-mezők ott élnek, nem a Kulcsban. Ezért az első feladat egy **ismételhető (idempotens, SKU/Woo-azonosító szerint upsertelő) importer** a Woo REST API-ról: termékek, variációk, kategóriák, leírások, SEO-mezők és slugok átvétele, a képek letöltése a storage-ba hash-elt kulcsokkal. A slugok megőrzésével a termék-URL-ek változatlanok maradhatnak (SEO). A Kulcs-Soft a készletet (és D9 szerint az árat) adja, cikkszám-párosítással. Az átállásig a termékszerkesztés helye a WooCommerce marad (egyirányú, ütemezett import) — így nincs kettős karbantartás. Emellett: termék- és variánsmodell, kategóriafa, admin-képfeltöltés, árlisták; terméklap- és kategória-render (Thymeleaf), JSON-LD `Product` az árral és elérhetőséggel; Meilisearch-termékindex frissítése változáskor (K2); kategória-mentéskor webhook a statikus build felé (K5).

*Elfogadás:* az importer többszöri futtatásra duplikáció nélkül átveszi a Woo-oldali módosításokat (új termék, átírt leírás, cserélt kép); egy Kulcs-Softban módosított ár a következő szinkron után a terméklapon és az indexben is friss; a terméklap HTML-je cookie nélkül byte-azonos két különböző látogatónak.

### M2. Kosár és session (5–10 nap)

Guest-cart cookie-azonosítóval, kosár-API (a statikus oldalak szigete is ezt hívja), `/api/session`, belépéskori kosár-összefésülés, készlet-állapot ellenőrzés kosárba tételkor.

*Elfogadás:* statikus tartalmi oldalról kosárba tett termék megjelenik a webshop kosaras oldalán, fülváltás és böngésző-újraindítás után is.

### M3. Checkout + fizetés (15–25 nap)

Szállítási mód és díjszámítás (díjtábla konfigurációból), címkezelés, KHPos-bekötés, rendelés-létrehozás, szamlazz.hu számla, futárcímke (MyGLS/Kvikk). **A hibaágak külön feladatlistát kapnak:** idempotens rendelésrögzítés (dupla kattintás/újratöltés ellen), fizetési callback késés/kimaradás kezelése időzített újraellenőrzéssel, „fizetve, de rendelés-rögzítés hibázott" eset riasztással, készlet-újraellenőrzés checkout-indításkor, egydarabos termékek kapcsolható 15 perces foglalása.

*Elfogadás:* a happy path mellett mind a felsorolt hibaágak teszteltek (integrációs teszt + kézi forgatókönyv); Playwright E2E a teljes vásárlási útra a CI-ban.

### M4. Tranzakciós e-mailek (4–6 nap)

Sablonrendszer (rendelés-visszaigazolás, fizetési visszajelzés, feladási értesítő tracking-linkkel, jelszó-reset), küldés sorba állítva, retry, kézbesítési napló. D6 előfeltétel.

### M5. Vásárlói fiók (8–12 nap)

Regisztráció/belépés (Spring Security), jelszó-reset, címek, rendeléstörténet, GDPR-adatexport és fióktörlés.

### M6. Rendeléskezelő admin — SPA (20–30 nap)

Refine/React-Admin a REST API fölött. Terjedelem-fegyelem: **csak a napi csomagolás–számlázás–feladás folyamat**: rendeléslista szűrőkkel, rendeléslap, státuszgép (új → fizetve → csomagolás → feladva → teljesítve), sztornó + sztornószámla, visszáru és részvisszatérítés, futárcímke-nyomtatás, termék- és képszerkesztés, kategória-kezelés, kupon-adminisztráció. Szerepkörök + audit log az ár- és rendelésmódosításokra. Minden kényelmi funkció (tömeges műveletek, statisztika) tudatosan a 2. körbe.

*Elfogadás:* egy próbarendelés a beérkezéstől a feladásig végigvihető az adminból, fejlesztői beavatkozás nélkül.

### M7. Kupon és akciók — alap (5–12 nap)

Kuponkód, százalékos/fix kedvezmény, érvényességi idő, minimum kosárérték, felhasználás-számlálás. Termékkör-szabályok és kombinálhatóság csak konkrét üzleti igényre.

### M-BLOG. Blog a webshopban (3–5 nap)

A `content/blog/` Markdown-cikkek renderelése (flexmark → Thymeleaf, `shared/` design): bloglista a régi lapozó-URL-eken, `Article` JSON-LD, Meilisearch-indexelés a közös sémába. **Ajánlott termékek** a front matterből (explicit SKU-lista + kategória-feltöltés limitig), a kategóriaoldal termékkártya-fragmentjével renderelve; elfogyott/kifutott termék automatikusan kimarad, hibás cikkszám naplózva kimarad. Frissesség: fordított hivatkozási térkép (termék → hivatkozó cikkek), termék-állapot/árváltáskor az érintett blog-URL-ek purge-ölése; publikálás merge → deploy → purge. AI-cikksablon + szerkesztői útmutató (`docs/blog-workflow.md`).

*Elfogadás:* próbacikk PR-ból élesedik ajánlott termékekkel; állapotváltás után az elfogyott termék kikerül az ajánlóból; a blog-URL-ek 1:1 egyeznek a WordPress-szel.

### M8. Feedek és mérés (8–12 nap)

Közös feed-generátor → Google Merchant XML + Meta-katalógus (naponta többszöri frissítés, `availability` az állapotmodellből). GA4 e-commerce események + Meta Pixel kliensoldalon; szerveroldali Meta Conversions API és GA4 Measurement Protocol a rendelésekre, esemény-deduplikációval. Consent-kezelő + **Consent Mode v2** a statikus és dinamikus oldalakon egységesen (a közös JS-sziget részeként, K1). Marketingplatform-események: feliratkozás, rendelés, kosárelhagyás, „értesítsen, ha elérhető".

*Elfogadás:* tesztrendelés megjelenik a GA4-ben és a Meta Events Managerben (dedupláltan); a feed validátor-hiba nélkül betölt a Merchant Centerbe.

### M9. Végső szinkron + soft launch (2–4 nap + 1–2 hét naptári idő)

Mivel a termékek az M1-es importerrel folyamatosan, ütemezetten jönnek át, itt már csak a **végső delta-import** fut, ezután a WooCommerce termékszerkesztése lezárul (írásvédett), és a szerkesztés helye az új admin lesz. Régi képútvonalak rewrite-ja, URL-térkép ellenőrzése — a slugok megőrzése miatt a termék-URL-ek ideális esetben változatlanok, 301 csak a ténylegesen változó útvonalakra kell. **A `/blog/*` routing itt vált át a WordPressről a webshopra** (M-BLOG élesedése). Soft launch: belső kör éles fizetéssel teszteli az új shopot, közben a régi még él; átállás után a WooCommerce csak-olvasásra, majd lekapcsolás. Vásárlóknak előzetes kommunikáció a jelszó-visszaállításról.

*Mérföldkő (= 2. fázis vége):* első éles, fizetett, számlázott, feladott rendelés; WP lekapcsolva; Search Console rendben.

## 4. Folyamatosan futó sáv (8–10 nap, elosztva)

Monitoring (uptime, app-metrikák, Sentry a checkout-frontendre), riasztások, napi DB-mentés + visszaállítási próba, rate limit a publikus API-n, függőség-frissítési rutin, ADR-ek (architektúra-döntési jegyzetek) vezetése.

## 5. Üzleti oldal párhuzamos teendői (nem fejlesztői munka)

- ÁSZF és adatkezelési tájékoztató frissítése az új folyamatra és adatfeldolgozókra (jogász).
- Szállítási díjtábla és kuponszabályok írásos definíciója (M3/M7 bemenete).
- E-mail-sablonok és checkout-mikroszövegek megírása (M3/M4 bemenete).
- Marketingplatform-választás (D4) és a meglévő feliratkozólista exportja.
- Vásárlói kommunikáció terve az átállásról és a jelszó-visszaállításról.

## 6. Mérföldkő-összefoglaló

| Mérföldkő | Tartalom |
|---|---|
| MK0 | Sprint 0 kész: staging él, CI megy, Kulcs-Soft PoC kiértékelve |
| MK1 | M1–M2: terméklapok élnek stagingen, kosár működik statikus oldalról is |
| MK2 | M3–M4: teljes vásárlás végigmegy stagingen éles teszt-fizetéssel |
| MK3 | M5–M6: admin használatra kész, próbarendelés teljes életciklusa |
| MK4 | M7, M-BLOG, M8: kupon + blog ajánlott termékekkel + mérés/feedek validálva |
| MK5 | Soft launch → éles átállás → WordPress lekapcsolva |
