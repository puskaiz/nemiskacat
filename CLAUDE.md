# CLAUDE.md — nemiskacat.hu webshop (B sáv)

## Projekt

Saját fejlesztésű webshop a WooCommerce kiváltására. Vásárlói oldal: Spring Boot + Thymeleaf + htmx; admin és POS: REST API + SPA (Refine). A termékkatalógus elsődleges forrása a WooCommerce (ismételhető import), a készlet/ár a Kulcs-Softból szinkronizálódik. **A blog is ebben az appban renderelődik** (Postgresben tárolt, szerveroldalon sanitált HTML-cikkekből, admin WYSIWYG-szerkesztővel — l. ADR 0008, 0009), ajánlott termékekkel. Specifikáció: `SPEC.md`, feladatlista: `TASKS.md`, teljes rendszerterv: `docs/TERV.md`.

## Stack (alapértelmezések — eltérés csak emberi jóváhagyással)

- Java 21, Spring Boot 3.x, Maven; PostgreSQL + Flyway; Thymeleaf + htmx a vásárlói oldalon
- REST: springdoc OpenAPI, abból generált TypeScript-kliens; hibaformátum: RFC 9457 problem+json
- Admin SPA: Refine + TypeScript, külön `admin-ui/` mappában
- Blog-tartalom: Postgresben tárolt, szerveroldalon sanitált HTML (jsoup safelist); szerkesztés TipTap WYSIWYG-gel. flexmark már csak a Markdown→HTML egyszeri backfillhez (l. ADR 0009; nem `content/blog/` fájlokban)
- Integrációk: meglévő belső starterek (KHPos, szamlazz.hu, MyGLS, Kvikk) + Kulcs-Soft adapter
- Tesztek: JUnit + Testcontainers (Postgres) az integrációkra; Playwright E2E a checkout-útra a CI-ban

## Modulszerkezet

```
domain/         # entitások + üzleti szabályok (készletállapot, kupon, árazás)
application/    # service-réteg — az EGYETLEN igazságforrás üzleti logikára
web/            # Thymeleaf-controllerek (vásárlói oldal + blog)
api/            # REST-controllerek (admin, POS, statikus oldal szigetei, feedek)
integrations/   # starterek + adapterek (Kulcs-Soft, pénztárgép, marketing)
content/blog/   # (kivezetve) — a blog Postgresben tárolódik, l. ADR 0008
admin-ui/       # Refine SPA
docs/           # SPEC.md, TERV.md, ADR-ek, döntésnapló
```

## Kőbe vésett szabályok

1. **Üzleti logika kizárólag a service-rétegben.** A Thymeleaf- és REST-controller vékony; ugyanazt a service-t hívják. Logika controllerben = hibás PR.
2. **Munkamenet-mentes cache-elhető HTML:** terméklap, kategóriaoldal és blogoldal HTML-jébe felhasználó-/kosárfüggő adat nem kerülhet. Kosárikon, belépett állapot: kizárólag a közös kliensoldali sziget (`/api/session`, no-store).
3. **Auth:** HttpOnly session cookie + CSRF (SameSite=Lax + token). JWT-t böngészős kliensnek bevezetni TILOS.
4. **Idempotencia:** a Woo-importer és a Kulcs-Soft szinkron többszöri futtatásra duplikáció nélkül upsertel (kulcs: SKU / Woo-ID). A rendelésrögzítés idempotens (kliens-generált rendelés-kulcs), a fizetési callback újrafeldolgozás-biztos.
5. **Készlet:** a weboldal származtatott ÁLLAPOTOT kap (készleten / elfogyott / átmenetileg nem elérhető / kifutott), nyers darabszámot soha. Elérhető készlet = utolsó szinkron − azóta keletkezett webes rendelések − POS-eladások. Levonás atomi, rendelésrögzítéskor.
6. **Pénz:** összegek minor unitban (fillér, `long`/`BigDecimal` skála 0), HUF; áfa-számítás a service-rétegben, tesztelve. Időpontok UTC-ben tárolva, megjelenítés Europe/Budapest.
7. **URL-ek és SEO:** termék- és blog-slugok a Woo-ból 1:1 átvéve, megváltoztatásuk TILOS. Terméklapon JSON-LD `Product` (ár + availability), blogon `Article`.
8. **Képek:** feltöltés a storage-ba hash-elt, megváltoztathatatlan kulccsal; az app képfeldolgozást NEM végez (átméretezés = CDN optimizer URL-paraméter).
9. **Blog:** blogcikkek és kezelt kategóriák Postgresben tárolva (nem `content/blog/` fájlokban). Szerkesztés az admin SPA-ban (TipTap WYSIWYG; minden írási út — admin mentés és import — szerveroldali HTML-sanitáláson megy át [jsoup safelist], Draft/Published állapot, borítókép, ajánlott termékek SKU szerint). Publikálás deploy nélkül. Nyilvános oldalak (`/blog`, `/blog/kategoria/{slug}`, `/blog/{slug}`) session-független, cache-elhető HTML; `Article` JSON-LD; az ajánlódobozban az elérhetőségi állapot jelenik meg, elfogyott/kifutott termék automatikusan kimarad. Jövőbeli szeletek: cache purge / fordított hivatkozási lista, Meilisearch-indexelés, AI-cikksablon. Lásd: ADR 0008, 0009.
10. **Nyelv:** felület és tartalom magyarul; kód, commitok, kommentek angolul.

## Tiltások

- Titok (API-kulcs, jelszó, DSN) soha a repóba; `.env` + CI secret. Éles kulcsokat Claude soha nem kap és nem kér.
- Éles deploy, éles adatbázis-művelet, éles fizetési hívás: csak ember. A CI stagingre deployol.
- Új framework/könyvtár bevezetése, DB-séma törő módosítása, a `shared/` design-rendszer (A sávból átvett) API-jának törése: csak emberi jóváhagyással.
- A WooCommerce éles oldalán írási művelet TILOS (az import csak olvas). Az átállásig a termékszerkesztés helye a Woo — az új admin termékszerkesztője az átállásig feature flag mögött.

## Munkamenet-szabályok

- Egy feladat = a `TASKS.md` egy tétele; a végén futtatható állapot, zöld tesztek, az elfogadási kritérium igazolása (parancs + kimenet), rövid összefoglaló.
- Minden számottevő architektúra-döntésről rövid ADR a `docs/adr/` alá.
- Hiányzó döntésnél ne találgass: `DECISION NEEDED` jelölés + összefoglalóban felsorolás, és haladj a nem blokkolt részekkel.
- A fizetési és készlet-hibaágakhoz tartozó tesztek nem hagyhatók ki és nem kapcsolhatók ki.
