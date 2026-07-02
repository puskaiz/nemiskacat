# nemiskacat.hu — WordPress/WooCommerce kiváltási terv

**Verzió:** 1.0 (vitaanyag) · **Dátum:** 2026. június 11.

---

## 1. Összefoglaló

A nemiskacat.hu jelenleg WordPress/WooCommerce alapon fut, amelynek üzemeltetése drága, a frissítések egyre több külső fejlesztői időt igényelnek, miközben a tartalom ritkán változik. A terv ezt a rendszert három, egymástól jól elhatárolt részre cseréli:

1. **Statikus tartalmi réteg** (~350 oldal): statikus oldalgenerátorral (SSG) készül, CDN-ről kerül kiszolgálásra, a keresést Meilisearch adja. A **blog a webshop-alkalmazásban renderelődik** (Markdown-forrásból, Git-alapú folyamattal), hogy a cikkek alján ajánlott termékek jelenhessenek meg élő árral és készletállapottal; új cikkek házon belül, AI segítségével, sablon alapján készülnek.
2. **Saját fejlesztésű webshop** Spring Boot alapon, a meglévő integrációs starterekre (KHPos, szamlazz.hu, MyGLS, Kvikk) építve. A vásárlói oldal Thymeleaf + htmx, az admin és a POS REST API + SPA.
3. **Új integrációk**: Google/Meta termékfeed és konverziómérés, valamint a SAPI marketingintegráció kiváltása.

A készlet- és ártörzs a Kulcs-Softban marad (későbbi cserével), a webshop szinkronizál belőle és saját elérhetőségi főkönyvet vezet.

**Nagyságrendi ráfordítás:** a webshop élesítéséig (WordPress lekapcsolásáig) ~105–165 embernap, a teljes terjedelem (B2B és POS modulokkal együtt) ~125–200 embernap. Két fejlesztővel az élesítés reálisan 4–6 hónap.

**A két legfontosabb korai kockázatcsökkentő lépés:** a Kulcs-Soft szinkron proof-of-concept az első hetekben, valamint a teljes URL-átirányítási térkép elkészítése a SEO-forgalom megőrzéséhez.

---

## 2. Kiinduló helyzet és célok

| | Jelenleg | Cél |
|---|---|---|
| Platform | WordPress + WooCommerce | Statikus tartalom (CDN) + saját Spring Boot webshop |
| Üzemeltetés | Külső fejlesztőcég, drága frissítések | Házon belüli Java-csapat, minimális mozgó alkatrész |
| Tartalom | ~350 oldal, ritkán változik | SSG + Git-alapú szerkesztés, AI-asszisztált blogírás |
| Termékek | < 1000 termék | Saját katalógus, Kulcs-Soft készlet/ár szinkronnal |
| Keresés | WordPress beépített | Meilisearch (tartalom + termék) |
| Marketing | SAPI integráció | Új platform (kiválasztandó) + Google/Meta integrációk |
| Speciális igények | Nem megoldott | B2B egyoldalas rendelés, bolti POS, egyedi checkout |

Nem cél: a Kulcs-Soft kiváltása (külön, későbbi projekt), többnyelvűség, piactér-integrációk — ezek tudatosan kimaradnak az első körből.

---

## 3. Célarchitektúra

### 3.1 Áttekintés

Minden a `nemiskacat.hu` domain alatt fut. A CDN útvonal alapján dönt:

- statikus tartalmi oldalak, blog → CDN-ről kiszolgált, buildkor generált HTML;
- terméklapok, kategóriaoldalak → Spring Boot + Thymeleaf, dinamikus render, rövid (30–60 mp) CDN mikro-cache-sel;
- `/api/*`, kosár, checkout, fiók → Spring Boot, cache nélkül;
- `/admin`, POS → SPA, REST API-ból dolgozik;
- képek → object storage + CDN + on-the-fly képoptimalizáló.

Egyetlen üzleti logikai (service) réteg van; erre ül rá egyrészt a Thymeleaf-controller (HTML), másrészt a REST-controller (JSON). A „mi megy HTML-ként, mi API-ként" kérdés így bármikor olcsón újratárgyalható.

### 3.2 Statikus tartalmi réteg

- **Generátor:** Astro, Hugo vagy Eleventy (kiválasztandó, ld. nyitott kérdések). A ~350 oldal és a blog a WordPressből exportálva (WXR / REST API) Markdown formátumba kerül; a tartalmi oldalakat az SSG rendereli, **a blogcikkeket a webshop** (ld. 3.3), az 1. fázisban a blog még a WordPressben él.
- **Szerkesztési folyamat:** Git-alapú. Az új blogcikkeket AI generálja sablon (front matter + szerkezet) alapján, kolléga átnézi, merge után automatikus build és CDN-frissítés fut. Nehéz CMS nem szükséges; ha nem fejlesztő kollégának kell felület, Decap CMS / TinaCMS húzható a repo fölé.
- **Build triggerek:** tartalmi merge; új termékkategória létrehozása az adminban (webhook), mert a menü a statikus HTML-be sül be; header/footer változás.
- **Keresés:** a build részeként a tartalom Meilisearch-indexbe kerül; a termékindexet a webshop frissíti termékváltozáskor. Üzemeltetés: Meilisearch Cloud vagy kis VPS (mentés + master key kezeléssel).

### 3.3 Terméklapok és kategóriaoldalak

- Spring Boot + Thymeleaf rendereli **futásidőben**, közvetlenül az adatbázisból. Ezer termék alatt ez pár ezredmásodperces render; külön build- és purge-gépezet nem épül.
- **CDN mikro-cache 30–60 másodperces TTL-lel**, purge nélkül: a forgalmi csúcsokat (hírlevél, botok) a CDN nyeli el, az elavulási ablak érzékelhetetlen. Kiegészítésként alkalmazás-szintű cache (Caffeine) a termékadatokra.
- Az ár és a készlet-**állapot** a HTML-be és a JSON-LD structured datába kerül (schema.org `Product` + `availability`), mindig frissen.
- **Cache-fegyelem:** a terméklap HTML-je munkamenet-független marad; a kosárikon és a belépett felhasználó a közös kliensoldali szigetből jön. Ez most ingyen van, és nyitva hagyja az ajtót a teljes CDN-cache későbbi bekapcsolására, ha a forgalom indokolja.
- Kifutott termék oldala alternatíva-ajánlóval élve marad (SEO), később 301 a kategóriára.
- **Blog:** a webshop rendereli `content/blog/` Markdown-fájlokból (flexmark + Thymeleaf), hosszú CDN-TTL + purge publikáláskor; ajánlott termékek a front matterből (SKU/kategória), szerveroldalon, elérhetőségi állapottal; termék → hivatkozó cikkek fordított térkép alapján állapot-/árváltáskor a blog-URL-ek is purge-ölődnek.

### 3.4 Közös fejléc, lábléc, session-sziget

- A header/footer HTML-fragment és CSS **egyetlen forrásból** él (közös `shared/` modul); a statikus build beilleszti az oldalakba, a webshop deploy-időben kapja meg Thymeleaf-fragmentként.
- A munkamenet-függő rész (kosár darabszám, belépett felhasználó) **soha nem kerül a HTML-be**: egy közös, CDN-ről töltött kis JS hívja a `/api/session` végpontot (`Cache-Control: no-store`). Kosár-cookie hiányában a hívás kimarad.
- A közös CSS verziózott URL-en él (`site.<hash>.css`); a fragment és a CSS mindig egy lépésben, együtt frissül — ezt a build pipeline kényszeríti ki.
- Fejléc-változás = teljes statikus rebuild + purge + webshop fragment-frissítés, egy pipeline-futásban.

### 3.5 Webshop — vásárlói oldal

- **Thymeleaf + htmx**: kosár, checkout, vásárlói fiók. Java-csapatnak otthonos, a checkoutnál a legkevésbé törékeny megoldás.
- **Autentikáció:** HttpOnly session cookie (nem JWT böngészőben), Spring Security. CSRF-védelem: SameSite=Lax + token a módosító kéréseken. Mivel minden egy domainen fut, CORS nincs.
- **Kosár:** szerveroldali, guest-cart cookie-azonosítóval; belépéskor összefésülés a fiók kosarával. Így a statikus oldalról kosárba tett termék is konzisztensen megmarad fülek és oldaltípusok között.

### 3.6 Admin és POS — REST + SPA

- A Spring Boot REST API-jára (OpenAPI-spec, abból generált TypeScript-kliens, problem+json hibaformátum) SPA épül.
- **Admin:** React-Admin vagy Refine keretrendszerrel — a CRUD-képernyők, listák, szűrők nagy része konfiguráció, ez a rendeléskezelő admin becslését a sáv aljához húzza.
- **POS:** gyors, billentyűzet/vonalkód-vezérelt felület; a fizetésnél az API-képes pénztárgépet egy **adapter-interfész** mögött hívja, hogy gépcsere esetén csak az adapter cserélődjön. A POS ugyanazt a készlet-service-t használja, mint a web — a két csatorna valós időben konzisztens.

### 3.7 Készletkezelés

- **Származtatott állapotmodell:** a weboldal soha nem nyers darabszámot kap, hanem állapotot: *készleten* (küszöb alatt „utolsó néhány darab"), *elfogyott* (+„értesítsen" e-mail-gyűjtő), *átmenetileg nem elérhető* (kézi flag), *kifutott*, opcionálisan *előrendelhető*. Az állapot-számítás egy helyen, a service-rétegben él.
- **Elérhetőségi főkönyv:** elérhető = utolsó Kulcs-Soft szinkron szerinti készlet − azóta keletkezett webes rendelések − azóta rögzített POS-eladások. A Kulcs-Soft könyvelési törzs marad, periodikus egyeztetéssel.
- **Túladás-védelem három ponton:** puha ellenőrzés kosárba tételkor → újraellenőrzés checkout-indításkor → atomi levonás rendelésrögzítéskor (optimista zárolás / DB-megkötés).
- **Egydarabos termékek:** termék- vagy kategória-szintű kapcsolóval rövid (pl. 15 perces) foglalás a checkout indításakor — az egyedi daraboknál a „két vevő versenyez" nem határeset, hanem fő forgatókönyv.
- A Google Merchant feed `availability` mezője az állapotból jön; a feed naponta többször frissül, hogy a hirdetés és a lap ne mondjon ellent egymásnak.

### 3.8 Képkezelés

- **Tárolás:** S3-kompatibilis object storage (pl. Bunny Storage vagy Cloudflare R2), nem az app szerver fájlrendszere. Az adatbázisban csak hivatkozás és metaadat (alt, sorrend).
- **Feltöltés:** admin SPA → API (validálás, EXIF-tisztítás) → storage. A storage-kulcs tartalom-hasht tartalmaz (`termek/1234/fo-a1b2c3.jpg`) → minden kép-URL megváltoztathatatlan, `Cache-Control: immutable, max-age=1 év`; képcsere = új URL, purge soha nem kell.
- **Méretváltozatok:** on-the-fly optimalizáló (Bunny Optimizer / Cloudflare Image Resizing): URL-paraméteres átméretezés, automatikus WebP/AVIF, triviális `srcset`. Java-oldali képfeldolgozó kód nem készül.
- **Kiszolgálás:** egy kép-alapdomain (pl. `kep.nemiskacat.hu`) szolgálja a terméklapokat, a statikus oldalakat, a blogot és a feedek kép-URL-jeit is.

### 3.9 Domain és routing

| Útvonal | Kiszolgáló | Cache |
|---|---|---|
| `/`, tartalmi oldalak, blog | CDN (statikus HTML) | hosszú TTL, build purge-öl |
| terméklapok, kategóriák | Spring Boot (Thymeleaf) | CDN mikro-cache 30–60 mp |
| `/api/*`, kosár, checkout, fiók | Spring Boot | nincs (no-store) |
| `/admin`, POS | SPA (statikus assetek) + REST API | assetek hosszú TTL |
| `kep.nemiskacat.hu` | object storage + optimizer | immutable, 1 év |

A CDN ezekre az útvonalakra szabály szerint kezeli a cookie-kat (statikus/cache-elt útvonalon eldobja).

---

## 4. Integrációk

### 4.1 Meglévő (Spring Boot starterek)

| Integráció | Szerep | Teendő |
|---|---|---|
| KHPos | fizetési kapu | bekötés a checkoutba; hibaágak (megszakadt fizetés, késő/elmaradó callback, dupla indítás) kiemelt teszteléssel |
| szamlazz.hu | NAV online számla | számla rendelésrögzítés/fizetés után; sztornószámla a visszáru-folyamatban |
| MyGLS, Kvikk | futárszolgálat | címkegenerálás, tracking-szám visszaírás, státuszkövetés |
| Kulcs-Soft | készlet- és ártörzs | ütemezett szinkron + eltéréskezelés; **PoC az első hetekben** (adatminőség-kockázat) |

### 4.2 Új fejlesztések

- **Google Merchant Center:** termékfeed (XML, naponta többszöri generálás) — ár, elérhetőség, kép-URL a katalógusból; konzisztens a terméklap JSON-LD-jével.
- **Meta katalógus:** ugyanabból a feed-generátorból, Meta-formátumban.
- **Mérés:** GA4 e-commerce események + Meta Pixel kliensoldalon; **szerveroldali Meta Conversions API és GA4 Measurement Protocol** a rendeléseseményekre (adblocker/iOS-veszteség ellen). **Consent Mode v2 kötelező** — e nélkül a Google Ads konverziómérés az EU-ban nem működik.
- **Marketingplatform (SAPI kiváltása):** kiválasztandó (Brevo, Klaviyo, vagy a SalesAutopilot megtartása új API-integrációval). A webshop eseményeket ad át: feliratkozás, rendelés, elhagyott kosár, „értesítsen, ha elérhető" — az automatizmusokat (pl. elhagyott kosár e-mail) a platform futtatja, nem a webshop.

---

## 5. Komponensbontás és becslések

Feltételezések: tapasztalt Java-fejlesztők, élesben bizonyított starterek, < 1000 termék, készlet/ár a Kulcs-Softból. A számok embernapok; a sáv alja az „egyszerű igények", a teteje a „kiderült, hogy bonyolultabb" eset.

| # | Komponens | Tartalom (röviden) | Becslés | Fázis |
|---|---|---|---|---|
| 1 | Statikus tartalom + keresés | WP-export, SSG-pipeline, AI-blog workflow, header/footer fragment-rendszer, Meilisearch | 10–18 | 1. |
| 2 | Termékkatalógus + Kulcs-Soft szinkron | termék, variánsok, kategóriafa, képek, árlisták; terméklap-render; Meilisearch-index; szinkron + eltéréskezelés | 15–25 | 2. |
| 3 | Kosár, session | guest-cart, összefésülés, kosár API, `/api/session`, készlet-ellenőrzés | 5–10 | 2. |
| 4 | Checkout + fizetés | szállítási mód/díj, KHPos, rendelés-létrehozás, számla, futárcímke; hibaágak | 15–25 | 2. |
| 5 | Rendeléskezelő admin (SPA) | rendeléslista, státuszgép, sztornó(számla), visszáru, részvisszatérítés, tracking | 20–30 | 2. |
| 6 | Vásárlói fiók | login/regisztráció, jelszó-reset, címek, rendeléstörténet, GDPR export/törlés | 8–12 | 2. |
| 7 | Tranzakciós e-mailek | sablonok, küldés, retry, kézbesítési napló | 4–6 | 2. |
| 8 | Kupon, akciók (alap) | kuponkód, %/fix kedvezmény, érvényesség, min. kosárérték | 5–12 | 2. |
| 9 | Feedek, mérés | Google/Meta feed, GA4 + CAPI, Consent Mode v2 | 8–12 | 2. (élesítéssel együtt) |
| 10 | Adat- és képmigráció | termékek, képek, alt-szövegek, URL-térkép | 3–5 | 2. |
| 11 | B2B modul | céges fiókok, egyedi árlisták, egyoldalas gyorsrendelés, átutalás határidővel | 10–15 | 3. |
| 12 | POS modul | eladói felület, vonalkód, készletlevonás, pénztárgép-adapter, nyugta/számla | 10–20 | 4. |
| 13 | Üzemeltetési alapok | CI/CD, staging, monitoring, riasztás, mentések, jogosultságok, audit log | 8–10 | folyamatos |

**Összesítés:**

- Webshop-élesítésig (1–10 + 13): **~105–165 embernap** → két fejlesztővel 4–6 hónap, eggyel 7–10 hónap (koordinációs és tesztelési overheaddel).
- Teljes terjedelem (B2B + POS is): **~125–200 embernap**.

A becslést leginkább torzító tényezők: termékvariánsok léte és mennyisége; a Kulcs-Soft export/API minősége; a KHPos starter éles hibaág-lefedettsége; admin scope creep; a csapat TypeScript/React-gyakorlata az admin SPA-nál.

---

## 6. Fázisterv és mérföldkövek

### 0. fázis — Alapozás (2–3 hét, részben párhuzamos az 1. fázissal)

Infra és CI/CD, staging környezet, domain-routing (CDN + útvonal-szabályok), projektváz (service réteg + Thymeleaf/REST controller-szerkezet), object storage + képoptimalizáló beállítása. **Kulcs-Soft szinkron proof-of-concept** — szándékosan korai: ha az adatminőség rossz, az a katalógus-becslést borítja, és ezt az első héten jobb megtudni, mint a harmadik hónapban.

*Mérföldkő:* staging él, deploy gombnyomásra megy, Kulcs-Soft PoC eredménye kiértékelve.

### 1. fázis — Statikus tartalom élesítése (a WooCommerce shop még fut)

WP-tartalom exportja, SSG-pipeline, közös header/footer fragment, Meilisearch-kereső, AI-blog workflow, 301-térkép a tartalmi oldalakra. Kockázatmentes első élesítés: a WordPress felülete máris zsugorodik.

*Mérföldkő:* tartalmi oldalak és blog CDN-ről, keresés él, új blogcikk a Git-folyamaton át publikálható.

### 2. fázis — Webshop MVP és WordPress-lekapcsolás

Katalógus + szinkron, kosár, checkout (KHPos + számla + futár), e-mailek, fiók, alap admin, egyszerű kupon, adat-/képmigráció — **és vele együtt a feedek + mérés** (ha futnak Google/Meta hirdetések, a mérési lyuk azonnal pénzbe kerül).

**Soft launch:** néhány napig mindkét rendszer él; az új shopot belső kör teszteli éles fizetéssel, ezután átáll a forgalom, a WooCommerce csak olvasásra marad, majd lekapcsol.

*Mérföldkő:* első éles, fizetett, számlázott, feladott rendelés az új rendszerben; WP lekapcsolva; Search Console-ban a 301-ek és az indexelés rendben.

### 3. fázis — B2B

Céges fiókok, árlisták, egyoldalas gyorsrendelés, átutalásos fizetés.

### 4. fázis — POS

Az új, API-képes pénztárgép beszerzése **után** indul (előtte az adapter nem tervezhető). Eladói felület, készlet-bekötés, nyugta/számla elágazás.

### Tudatos kihagyások az MVP-ből

Elhagyott-kosár-automatizmus (a marketingplatform megoldja az átadott eseményből), termékértékelések, kívánságlista, többnyelvűség, admin kényelmi funkciók (tömeges műveletek, statisztikák). Mindegyik napokat enne, egyik sem élesítési feltétel.

---

## 7. Migráció és SEO

- **URL-térkép:** minden régi URL (tartalom, termék, kategória, kép) → új URL vagy 301. A térkép a 2. fázis *belépési feltétele*, nem utómunka.
- **Structured data paritás:** a WooCommerce által adott `Product` JSON-LD-t az új terméklap is adja (ár, elérhetőség, kép), különben rich snippetek esnek ki.
- **Sitemap + Search Console:** új sitemap élesítéskor, indexelés és 404/átirányítási hibák napi figyelése az átállás utáni hetekben.
- **Képek:** áttöltés a storage-ba; a régi `wp-content/uploads` útvonalakra CDN-rewrite vagy átirányítás (Google Images-forgalom és külső hivatkozások miatt). A migráció alkalom az alt-szövegek és fájlnevek rendbetételére.
- **Vásárlói fiókok:** a jelszó-hash-ek nem vihetők át — élesítés után jelszó-visszaállítás kérése; erről előre kommunikálni kell a vásárlóknak.
- **Rendeléstörténet:** az új rendszerbe nem migráljuk; számviteli okokból archívumként megőrzendő (8 év), olvasható formában.

## 8. Megfelelőség (magyar specifikumok)

- **NAV online számla:** a szamlazz.hu integráció fedi (webes értékesítés).
- **Bolti értékesítés:** pénztárgép-kötelezettség — a saját POS modul önmagában nem váltja ki. Az API-képes (e-pénztárgép szabályozásnak megfelelő) gép kiválasztását és a folyamatot **könyvelővel egyeztetni** a POS-fázis tervezése előtt.
- **GDPR:** adatkezelési tájékoztató frissítése az új adatfeldolgozókkal (CDN, storage, marketingplatform, mérés); fiók-export/törlés funkció; adatminimalizálás a logokban.
- **Cookie consent + Consent Mode v2:** consent-kezelő a statikus és dinamikus oldalakon egységesen (a közös JS-sziget része lehet).
- **Fogyasztóvédelem:** ÁSZF, 14 napos elállás, békéltető testületi tájékoztatás, panaszkezelés — a meglévő tartalmak átvihetők, de az új checkout-folyamattal jogásszal érdemes átnézetni.

## 9. Üzemeltetés

- **Rendelkezésre állás:** két app-példány vagy legalább automatikus újraindítás + riasztás; a statikus réteg és a képek a CDN miatt az app kiesésekor is élnek.
- **Monitoring:** uptime-ellenőrzés, alkalmazás-metrikák, hibakövetés backendre és a checkout-frontendre (pl. Sentry) az élesítéstől.
- **Mentés:** napi adatbázis-mentés + rendszeres visszaállítási próba; storage-replikáció vagy verziózás.
- **Biztonság:** függőség-frissítések ütemezve (ez most már a ti felelősségetek, nem a WP-fejlesztőé — de kiszámítható), admin/POS szerepkörök, audit log a rendelés- és ármódosításokra, rate limit a publikus API-n.
- **Tesztek:** a checkout teljes útjára E2E teszt (Playwright) a CI-ban; a fizetési hibaágakra integrációs tesztek.

## 10. Kockázatok

| Kockázat | Hatás | Kezelés |
|---|---|---|
| Kulcs-Soft adatminőség / export korlátai | katalógus-becslés borul | PoC a 0. fázisban; eltéréslista, kézi javítási kör |
| Checkout-hibaágak (fizetés megszakad, callback késik) | bevételkiesés, kettős rendelés | idempotens rendelésrögzítés, callback-újrafeldolgozás, dedikált tesztkör |
| Admin scope creep | csúszás | MVP-admin = csak a napi csomagolás–számlázás–feladás; minden más 2. kör |
| TypeScript/React-kompetencia hiánya | admin/POS csúszik | React-Admin/Refine (kevés egyedi kód); szükség esetén külső indítás házon belüli átvétellel |
| SEO-visszaesés átálláskor | organikus forgalom esik | teljes 301-térkép, structured data paritás, napi Search Console-figyelés |
| Pénztárgép-API ismeretlen | POS-becslés széles | gépválasztás után PoC az adapterre; POS-fázis csak ezután |
| Egyszemélyes tudás | busz-faktor | dokumentált architektúra-döntések (ADR), páros munka a kritikus modulokon |

## 11. Nyitott kérdések / döntésre vár

1. SSG-választás (Astro / Hugo / Eleventy) — javaslat: Astro, ha kell komponens-rugalmasság; Hugo, ha a sebesség és az egyszerűség a fő szempont.
2. CDN- és storage-szolgáltató (Bunny vs. Cloudflare) — ár, EU-régió, optimizer alapján.
3. Marketingplatform (Brevo / Klaviyo / SalesAutopilot megtartása új integrációval).
4. Hosting a Spring Bootnak (hazai VPS / felhő) és a Meilisearchnek (Cloud vs. self-host).
5. Kell-e termékvariáns-kezelés (méret/szín), és milyen mélységben — közvetlen hatása van a 2. komponens becslésére.
6. Vannak-e futó Google Shopping / Meta hirdetések az átállás idején (a feedek ütemezését ez határozza meg).
7. Az új pénztárgép típusa és API-ja (4. fázis előfeltétele).
8. Ki(k) a belső tartalomszerkesztők — kell-e Decap/Tina felület a Git fölé.

## 12. Döntésnapló (eddig meghozott irányok)

- Vásárlói oldal: **Thymeleaf + htmx**; admin és POS: **REST API + SPA** (React-Admin/Refine irány).
- Egy service-réteg, rajta Thymeleaf- és REST-controllerek — üzleti logika nem duplikálódik.
- Terméklapok: **dinamikus render + 30–60 mp CDN mikro-cache**, purge-gépezet nélkül; teljes CDN-cache később bekapcsolható, mert a HTML munkamenet-független marad.
- Statikus tartalom: SSG + Git-workflow; a blog a webshopban renderelődik (ajánlott termékek miatt), forrása ott is Git-es Markdown, AI-asszisztált írás sablonból, emberi review-val.
- Header/footer: egy forrásból, deploy-időben terítve; munkamenet-adat kizárólag kliensoldali szigetből.
- Auth: HttpOnly session cookie + CSRF-védelem; minden egy domainen, CORS nélkül.
- Készlet: származtatott állapotmodell; elérhetőségi főkönyv (szinkron − web − POS); háromszintű túladás-védelem; egydarabos termékekre kapcsolható checkout-foglalás.
- Képek: object storage, hash-elt immutable URL-ek, on-the-fly optimalizáló — Java-oldali képfeldolgozás nélkül.
- Kulcs-Soft marad a könyvelési törzs; cseréje külön projekt.
