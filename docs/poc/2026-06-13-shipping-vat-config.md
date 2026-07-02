# Shipping fee table & VAT config — extracted from the live Woo DB

**Date:** 2026-06-13 · **Source:** `wp_db` (read-only) — shipping zone/method/table-rate
tables + `woocommerce_*_settings` options. Input for T9 (checkout fee calculation).
Resolves the T9 `[EMBER]` fee-table blocker from data; the ÁSZF/checkout copy is
still pending business input.

## Shipping (single zone: Magyarország / HU)

Woo stores the table-rate amounts **net** (`prices_include_tax=no`); shipping VAT
is 27%. Gross = net × 1.27 — all amounts land on round forint values, confirming
the interpretation:

| Mód | Feltétel | Nettó (Woo) | **Bruttó** |
|---|---|---|---|
| GLS futárszolgálat | súly 0 – 0,9 kg | 669,30 | **850 Ft** |
| GLS futárszolgálat | súly 0,9 – 5 kg | 1 181,10 | **1 500 Ft** |
| GLS futárszolgálat | súly 5 – 500 kg | 1 338,50 | **1 700 Ft** |
| GLS futárszolgálat INGYENES | kosárérték ≥ 30 708 nettó (≈ **39 000 Ft bruttó**) | 0 | **0 Ft** |
| Magyar Posta házhozszállítás (min. 8 munkanap) | flat rate | 6 220,47 | **7 900 Ft** |
| Személyes átvétel üzletünkben | — | 0 | **0 Ft** |

Confirmed by the business (2026-06-13):

- **Orphan rate row** (rate_id 6, 2 756 net, detached `shipping_method_id=3`):
  dead config — excluded.
- **Free-shipping threshold:** implemented as **gross cart total ≥ 39 000 Ft**.
- Weight unit is **kg**; our variants store `weight_grams` (imported as kg×1000).
- Missing product weights count as 0 in the GLS bands — 14 products affected,
  tracked in `docs/TODO.md` (to be fixed in Woo; one is a digital product that
  needs a no-shipping flag instead).

## VAT

- Prices in Woo (and in our import) are **gross** (`prices_include_tax=yes`), HUF.
- Rates: **27% standard**, **5% reduced** (`reduced-rate` tax class — 11 products,
  books), shipping 27%. Our `product.tax_class` carries the Woo class:
  `'' = 27%`, `reduced-rate = 5%`.
- T9 must compute VAT breakdown from gross (HUF rounding) per tax class.
