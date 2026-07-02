import { useEffect, useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import { apiFetch, API_BASE } from "../api/http";

// Controlled multiselect for products (by SKU). Mirrors CategorySelect's inline-style
// and interaction conventions: autocomplete dropdown, chips for selected values.
// Options are loaded once from GET /api/admin/products?size=500 (products now include sku).
// Value is a list of SKUs; products with null sku are excluded.

const filterBox: CSSProperties = { display: "flex", alignItems: "center", minWidth: 220, border: "1px solid var(--border)", borderRadius: 999, padding: "5px 14px", background: "var(--surface)" };
const selChip: CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, borderRadius: 999, padding: "2px 6px 2px 10px", fontSize: 12, fontWeight: 600, color: "var(--accent-fg)", background: "var(--accent-soft)", border: "1px solid var(--border)" };
const chipX: CSSProperties = { display: "inline-flex", alignItems: "center", justifyContent: "center", width: 16, height: 16, borderRadius: 999, fontSize: 13, lineHeight: 1, color: "var(--muted)", cursor: "pointer" };

interface ProductOption {
  sku: string;
  name: string;
}

interface Props {
  /** Selected SKUs. */
  value: string[];
  onChange: (skus: string[]) => void;
}

export function ProductSelect({ value, onChange }: Props) {
  const { t } = useTranslation();
  const [options, setOptions] = useState<ProductOption[]>([]);
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);

  useEffect(() => {
    apiFetch(`${API_BASE}/products?size=500&page=0`)
      .then((r) => r.json())
      .then((rows: Array<{ sku?: string | null; name: string }>) =>
        setOptions(rows.filter((p) => p.sku).map((p) => ({ sku: p.sku!, name: p.name }))),
      )
      .catch(() => { /* silently ignore fetch errors — empty options */ });
  }, []);

  const productName = (sku: string) => options.find((o) => o.sku === sku)?.name ?? sku;

  const add = (sku: string) => {
    if (!value.includes(sku)) onChange([...value, sku]);
    setQuery("");
    setOpen(false);
  };
  const remove = (sku: string) => onChange(value.filter((s) => s !== sku));

  const needle = query.trim().toLocaleLowerCase("hu");
  const matches = options.filter(
    (o) =>
      !value.includes(o.sku) &&
      (o.name.toLocaleLowerCase("hu").includes(needle) || o.sku.toLocaleLowerCase("hu").includes(needle)),
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
      <div style={{ position: "relative" }}>
        <div style={filterBox} onClick={() => setOpen(true)}>
          <input
            value={query}
            placeholder={t("blog:productSearch")}
            onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
            onFocus={() => setOpen(true)}
            onKeyDown={(e) => { if (e.key === "Enter" && matches.length) add(matches[0].sku); }}
            style={{ flex: 1, minWidth: 120, border: "none", outline: "none", background: "transparent", fontSize: 13, color: "var(--text)", padding: "2px 0" }}
          />
          <span style={{ color: "var(--muted)", marginLeft: 6 }}>▾</span>
        </div>
        {open && (
          <>
            <div onClick={() => setOpen(false)} style={{ position: "fixed", inset: 0, zIndex: 40 }} />
            <div style={{ position: "absolute", top: 40, left: 0, zIndex: 41, minWidth: 220, maxHeight: 280, overflowY: "auto", background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 3, boxShadow: "0 8px 24px rgba(16,24,40,.18)" }}>
              {matches.length === 0 ? (
                <div style={{ padding: "8px 12px", fontSize: 13, color: "var(--faint)" }}>{t("blog:productNoMatch")}</div>
              ) : (
                matches.slice(0, 20).map((o) => (
                  <div
                    key={o.sku}
                    onClick={() => add(o.sku)}
                    style={{ padding: "8px 12px", fontSize: 13, cursor: "pointer", color: "var(--text)" }}
                  >
                    {o.name} <span style={{ color: "var(--muted)", fontSize: 11 }}>{o.sku}</span>
                  </div>
                ))
              )}
            </div>
          </>
        )}
      </div>

      {value.length > 0 && (
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
          {value.map((sku) => (
            <span key={sku} style={selChip}>
              {productName(sku)}
              <span style={chipX} title={t("blog:productRemove")} onClick={() => remove(sku)}>×</span>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
