import { useState, type CSSProperties } from "react";
import { useTranslation } from "react-i18next";
import type { ProductCategoryRef } from "../types";

// Rounded category selector (autocomplete): a pill-shaped box holding the search
// input that opens the category dropdown; selected categories render as chips below.
// Controlled — the parent owns the selected slugs and any side effects (e.g. resetting
// pagination on the list page). Used by the products list (filter) and the product
// detail editor (assigning a product's categories).

const filterBox: CSSProperties = { display: "flex", alignItems: "center", minWidth: 220, border: "1px solid var(--border)", borderRadius: 999, padding: "5px 14px", background: "var(--surface)" };
const selChip: CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, borderRadius: 999, padding: "2px 6px 2px 10px", fontSize: 12, fontWeight: 600, color: "var(--accent-fg)", background: "var(--accent-soft)", border: "1px solid var(--border)" };
const chipX: CSSProperties = { display: "inline-flex", alignItems: "center", justifyContent: "center", width: 16, height: 16, borderRadius: 999, fontSize: 13, lineHeight: 1, color: "var(--muted)", cursor: "pointer" };

interface Props {
  categories: ProductCategoryRef[];
  /** Selected category slugs. */
  value: string[];
  onChange: (slugs: string[]) => void;
  /** Override the search input placeholder (defaults to products:filterSearch). */
  placeholderText?: string;
  /** Override the chip remove button title (defaults to products:filterRemove). */
  removeTitle?: string;
}

export function CategorySelect({ categories, value, onChange, placeholderText, removeTitle }: Props) {
  const { t } = useTranslation();
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);

  const catName = (slug: string) => categories.find((c) => c.slug === slug)?.name ?? slug;

  const add = (slug: string) => {
    if (!value.includes(slug)) onChange([...value, slug]);
    setQuery("");
    setOpen(false);
  };
  const remove = (slug: string) => onChange(value.filter((s) => s !== slug));

  // Autocomplete: case-insensitive (Hungarian) name match, hiding already-selected.
  const needle = query.trim().toLocaleLowerCase("hu");
  const matches = categories.filter(
    (c) => !value.includes(c.slug) && c.name.toLocaleLowerCase("hu").includes(needle),
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
      <div style={{ position: "relative" }}>
        <div style={filterBox} onClick={() => setOpen(true)}>
          <input
            value={query}
            placeholder={placeholderText ?? t("products:filterSearch")}
            onChange={(e) => { setQuery(e.target.value); setOpen(true); }}
            onFocus={() => setOpen(true)}
            onKeyDown={(e) => { if (e.key === "Enter" && matches.length) add(matches[0].slug); }}
            style={{ flex: 1, minWidth: 120, border: "none", outline: "none", background: "transparent", fontSize: 13, color: "var(--text)", padding: "2px 0" }}
          />
          <span style={{ color: "var(--muted)", marginLeft: 6 }}>▾</span>
        </div>
        {open && (
          <>
            <div onClick={() => setOpen(false)} style={{ position: "fixed", inset: 0, zIndex: 40 }} />
            <div style={{ position: "absolute", top: 40, left: 0, zIndex: 41, minWidth: 220, maxHeight: 280, overflowY: "auto", background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 3, boxShadow: "0 8px 24px rgba(16,24,40,.18)" }}>
              {matches.length === 0 ? (
                <div style={{ padding: "8px 12px", fontSize: 13, color: "var(--faint)" }}>{t("products:filterNoMatch")}</div>
              ) : (
                matches.map((c) => (
                  <div
                    key={c.slug}
                    onClick={() => add(c.slug)}
                    style={{ padding: "8px 12px", fontSize: 13, cursor: "pointer", color: "var(--text)" }}
                  >
                    {c.name}
                  </div>
                ))
              )}
            </div>
          </>
        )}
      </div>

      {value.length > 0 && (
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
          {value.map((slug) => (
            <span key={slug} style={selChip}>
              {catName(slug)}
              <span style={chipX} title={removeTitle ?? t("products:filterRemove")} onClick={() => remove(slug)}>×</span>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
