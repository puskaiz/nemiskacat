import React, { useEffect, useRef, useState, type ChangeEvent, type CSSProperties } from "react";
import DOMPurify from "dompurify";
import { useGetIdentity, useList, useShow } from "@refinedev/core";
import { useNavigate } from "react-router-dom";
import { App, Collapse } from "antd";
import { useTranslation } from "react-i18next";
import { StatusPill } from "../../components/ui/StatusPill";
import { RichTextEditor } from "../../components/RichTextEditor";
import { CategorySelect } from "../../components/CategorySelect";
import { apiFetch, API_BASE } from "../../api/http";
import type { AttributeValueOption, AttributeView, PriceBasis, PriceUpdate, ProductCategoryRef, ProductDetail, ProductVariantView, ProductStatus } from "../../types";

// Faithful 1:1 port of the prototype product-detail layout (Admin Mid-fi HU.dc.html,
// the `detail` block, lines ~270-295), rebuilt as plain divs + inline styles +
// var(--…) tokens to match src/pages/orders/show.tsx. Read-only (P1) by default;
// when the `product-editor` feature flag is on for the admin (delivered via
// /api/admin/auth/me → identity.productEditorEnabled) the content fields become
// editable inputs with a Mentés (Save) button calling PUT /api/admin/products/{id}.
// Price / variants / images stay READ-ONLY in both modes (they're P2b/P2c).

const huf = (n: number | null) => (n == null ? "—" : `${n.toLocaleString("hu-HU")} Ft`);
// Catalog status → StatusPill tone keys (green = published, gray = draft).
const tone = (s: ProductStatus) => (s === "PUBLISHED" ? "active" : "neutral");

const card: CSSProperties = { background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 2, boxShadow: "0 1px 2px rgba(16,24,40,.04)" };
const sideCard: CSSProperties = { ...card, padding: 16, display: "flex", flexDirection: "column", gap: 12 };
const fieldLabel: CSSProperties = { fontSize: 12, color: "var(--muted)", fontWeight: 600, marginBottom: 6 };
const readField: CSSProperties = { border: "1px solid var(--border)", borderRadius: 3, padding: "9px 11px", fontSize: 14, background: "var(--input)" };
const sideTitle: CSSProperties = { fontSize: 13, fontWeight: 700 };
// Editable input box — mirrors pages/login.tsx's `field` style.
const editField: CSSProperties = { border: "1px solid var(--border)", borderRadius: 3, padding: "11px 13px", fontSize: 14, background: "var(--input)", color: "var(--text)", width: "100%", outline: "none", fontFamily: "inherit", boxSizing: "border-box" };
const accentBtn: CSSProperties = { border: 0, borderRadius: 999, padding: "10px 22px", fontSize: 14, fontWeight: 600, color: "#fff", background: "var(--accent)", cursor: "pointer", fontFamily: "inherit" };
const smallBtn: CSSProperties = { border: "1px solid var(--border)", borderRadius: 3, padding: "4px 10px", fontSize: 12, fontWeight: 600, background: "var(--surface)", color: "var(--text)", cursor: "pointer", fontFamily: "inherit", lineHeight: 1.4 };
const smallBtnAccent: CSSProperties = { ...smallBtn, border: 0, background: "var(--accent)", color: "#fff" };
// Shared key-column width so every variant property label aligns across all rows/variants.
const VARIANT_KEY_WIDTH = 96;
const variantPropKey: CSSProperties = { fontSize: 12, color: "var(--muted)", fontWeight: 600 };
const variantPropVal: CSSProperties = { color: "var(--text)" };

export const ProductShow = () => {
  const { t } = useTranslation();
  const nav = useNavigate();
  const { message } = App.useApp();
  const { data: identity } = useGetIdentity<{ productEditorEnabled?: boolean }>();
  const editable = !!identity?.productEditorEnabled;
  const { queryResult } = useShow<ProductDetail>();
  const product = queryResult?.data?.data;

  if (!product) {
    return <div style={{ color: "var(--muted)" }}>{t("loading")}</div>;
  }

  const statusLabel = product.status === "PUBLISHED" ? t("products:published") : t("products:draft");
  const defaultVariant = product.variants[0];
  const totalStock = product.variants.reduce((sum, v) => sum + (v.stockQty ?? 0), 0);

  if (editable) {
    return <EditableProductDetail product={product} refetch={() => queryResult?.refetch()} message={message} />;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div onClick={() => nav("/products")} style={{ fontSize: 13, color: "var(--accent-fg)", fontWeight: 600, cursor: "pointer" }}>← {t("products:back")}</div>

      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <div style={{ fontSize: 23, fontWeight: 700 }}>{product.name}</div>
        <StatusPill status={tone(product.status)} label={statusLabel} />
      </div>

      <div style={{ display: "flex", gap: 16, alignItems: "flex-start" }}>
        {/* left card: name, description, images, variants */}
        <div style={{ flex: 1.6, ...card, padding: 18, display: "flex", flexDirection: "column", gap: 16 }}>
          <div>
            <div style={fieldLabel}>{t("products:name")}</div>
            <div style={readField}>{product.name}</div>
          </div>

          <div>
            <div style={fieldLabel}>{t("products:description")}</div>
            {product.description ? (
              <div style={{ ...readField, minHeight: 96 }} dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(product.description) }} />
            ) : (
              <div style={{ ...readField, minHeight: 96, color: "var(--faint)" }}>—</div>
            )}
          </div>

          <div>
            <div style={fieldLabel}>{t("products:images")}</div>
            <div style={{ display: "flex", gap: 10, alignItems: "flex-start", flexWrap: "wrap" }}>
              {product.images.length > 0 ? (
                product.images.map((img, i) => (
                  <div key={i} style={{ width: i === 0 ? 104 : 72, height: i === 0 ? 104 : 72, borderRadius: 3, background: "var(--fill)", border: "1px solid var(--border)", position: "relative", overflow: "hidden" }}>
                    <img src={img.url} alt={img.alt ?? product.name} style={{ width: "100%", height: "100%", objectFit: "cover", display: "block" }} />
                    {i === 0 && (
                      <span style={{ position: "absolute", top: 5, left: 5, background: "var(--accent)", color: "#fff", fontSize: 10, fontWeight: 600, borderRadius: 3, padding: "1px 6px" }}>{t("products:cover")}</span>
                    )}
                  </div>
                ))
              ) : (
                <>
                  <div style={{ width: 104, height: 104, borderRadius: 3, background: "var(--fill)", border: "1px solid var(--border)" }} />
                  <div style={{ width: 72, height: 72, borderRadius: 3, background: "var(--fill)", border: "1px solid var(--border)" }} />
                  <div style={{ width: 72, height: 72, borderRadius: 3, background: "var(--fill)", border: "1px solid var(--border)" }} />
                </>
              )}
            </div>
          </div>

          <div>
            <div style={{ display: "flex", alignItems: "center", marginBottom: 8 }}>
              <div style={{ fontSize: 13, fontWeight: 700 }}>{t("products:variants")}</div>
              <div style={{ flex: 1 }} />
              <div style={{ fontSize: 12, color: "var(--muted)" }}>{t("products:variantsHint")}</div>
            </div>
            <Collapse
              accordion
              bordered
              style={{ background: "var(--surface)" }}
              items={product.variants.map((v) => ({
                key: String(v.id),
                label: <span style={{ fontSize: 13, fontWeight: 600 }}>{v.label}</span>,
                children: (
                  // Two-column aligned grid: every property is a key/value row stacked
                  // vertically. The key column has a fixed shared width so all labels
                  // align; the value column takes the rest (1fr) so all values align too.
                  <div style={{ display: "grid", gridTemplateColumns: `${VARIANT_KEY_WIDTH}px 1fr`, columnGap: 12, rowGap: 8, fontSize: 13 }}>
                    <div style={variantPropKey}>{t("products:sku")}</div>
                    <div style={{ ...variantPropVal, fontFamily: "monospace", color: "var(--muted)", fontSize: 12 }}>{v.sku || "—"}</div>

                    <div style={variantPropKey}>{t("products:price")}</div>
                    <div style={variantPropVal}>{huf(v.regularPriceHuf)}</div>

                    <div style={variantPropKey}>{t("products:stock")}</div>
                    <div style={variantPropVal}>{v.stockQty}</div>

                    {v.attributeValues.map((av) => (
                      <React.Fragment key={av.id}>
                        <div style={variantPropKey}>{av.attributeLabel}</div>
                        <div style={variantPropVal}>{av.valueLabel}</div>
                      </React.Fragment>
                    ))}
                  </div>
                ),
              }))}
            />
          </div>
        </div>

        {/* right column: pricing & stock + organization */}
        <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 16 }}>
          <div style={sideCard}>
            <div style={sideTitle}>{t("products:pricingStock")}</div>
            <div>
              <div style={fieldLabel}>{t("products:priceHuf")}</div>
              <div style={readField}>{huf(defaultVariant?.regularPriceHuf ?? null)}</div>
            </div>
            <div>
              <div style={fieldLabel}>{t("products:vat")}</div>
              <div style={readField}>{product.vatRatePercent == null ? "—" : `${product.vatRatePercent}%`}</div>
            </div>
            <div>
              <div style={fieldLabel}>{t("products:totalStock")}</div>
              <div style={{ ...readField, color: "var(--muted)" }}>{totalStock} {t("products:totalStockHint")}</div>
            </div>
          </div>

          <div style={{ ...sideCard, gap: 10 }}>
            <div style={sideTitle}>{t("products:organization")}</div>
            <div>
              <div style={fieldLabel}>{t("products:category")}</div>
              <div style={readField}>{product.categories[0]?.name ?? "—"}</div>
            </div>
            <div>
              <div style={fieldLabel}>{t("products:status")}</div>
              <div style={readField}>{statusLabel}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

// --- Editable variant (product-editor flag on) -----------------------------
// Same two-column layout; the webshop-content fields are real inputs and a
// Mentés button persists them via PUT /api/admin/products/{id}. Price, VAT,
// variants and images remain read-only (P2b/P2c).

interface EditableProps {
  product: ProductDetail;
  refetch: () => void;
  message: ReturnType<typeof App.useApp>["message"];
}

const EditableProductDetail = ({ product, refetch, message }: EditableProps) => {
  const { t } = useTranslation();
  const nav = useNavigate();

  const { data: catData } = useList<ProductCategoryRef>({ resource: "categories", pagination: { mode: "off" } });
  const allCategories = catData?.data ?? [];

  const [name, setName] = useState(product.name);
  const [shortDescription, setShortDescription] = useState(product.shortDescription ?? "");
  const [description, setDescription] = useState(product.description ?? "");
  const [seoTitle, setSeoTitle] = useState(product.seoTitle ?? "");
  const [metaDescription, setMetaDescription] = useState(product.metaDescription ?? "");
  const [status, setStatus] = useState<ProductStatus>(product.status);
  const [categorySlugs, setCategorySlugs] = useState<string[]>(product.categories.map((c) => c.slug));
  const [busy, setBusy] = useState(false);
  const [nameError, setNameError] = useState(false);

  // Reseed once when the loaded product identity changes (e.g. after a refetch
  // or navigating to a different product). Keyed on id so in-progress edits to
  // the same product are not clobbered.
  useEffect(() => {
    setName(product.name);
    setShortDescription(product.shortDescription ?? "");
    setDescription(product.description ?? "");
    setSeoTitle(product.seoTitle ?? "");
    setMetaDescription(product.metaDescription ?? "");
    setStatus(product.status);
    setCategorySlugs(product.categories.map((c) => c.slug));
    setNameError(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [product.id]);

  const statusLabel = status === "PUBLISHED" ? t("products:published") : t("products:draft");
  const defaultVariant = product.variants[0];
  const totalStock = product.variants.reduce((sum, v) => sum + (v.stockQty ?? 0), 0);

  const save = async () => {
    if (busy) return;
    setBusy(true);
    setNameError(false);
    try {
      const res = await apiFetch(`${API_BASE}/products/${product.id}`, {
        method: "PUT",
        body: JSON.stringify({ name, shortDescription, description, seoTitle, metaDescription, status, categorySlugs }),
      });
      if (res.ok) {
        message.success(t("products:saved"));
        refetch();
      } else {
        if (res.status === 400) setNameError(true);
        message.error((await res.text().catch(() => "")) || t("products:saveFailed"));
      }
    } catch {
      message.error(t("products:saveFailed"));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div onClick={() => nav("/products")} style={{ fontSize: 13, color: "var(--accent-fg)", fontWeight: 600, cursor: "pointer" }}>← {t("products:back")}</div>

      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <div style={{ fontSize: 23, fontWeight: 700 }}>{product.name}</div>
        <StatusPill status={tone(status)} label={statusLabel} />
        <div style={{ flex: 1 }} />
        <button type="button" disabled={busy} style={{ ...accentBtn, cursor: busy ? "wait" : "pointer" }} onClick={save}>{t("products:save")}</button>
      </div>

      <div style={{ display: "flex", gap: 16, alignItems: "flex-start" }}>
        {/* left card: editable content + read-only images/variants */}
        <div style={{ flex: 1.6, ...card, padding: 18, display: "flex", flexDirection: "column", gap: 16 }}>
          <div>
            <div style={fieldLabel}>{t("products:editName")}</div>
            <input style={{ ...editField, ...(nameError ? { borderColor: "#C0392B" } : null) }} value={name} onChange={(e) => setName(e.target.value)} />
            {nameError && <div style={{ fontSize: 12, color: "#C0392B", marginTop: 5 }}>{t("products:saveFailed")}</div>}
          </div>

          <div>
            <div style={fieldLabel}>{t("products:editShortDesc")}</div>
            <input style={editField} value={shortDescription} onChange={(e) => setShortDescription(e.target.value)} />
          </div>

          <div>
            <div style={fieldLabel}>{t("products:editDescription")}</div>
            <RichTextEditor value={description} onChange={setDescription} />
          </div>

          <div>
            <div style={fieldLabel}>{t("products:editSeoTitle")}</div>
            <input style={editField} value={seoTitle} onChange={(e) => setSeoTitle(e.target.value)} />
          </div>

          <div>
            <div style={fieldLabel}>{t("products:editMeta")}</div>
            <textarea style={{ ...editField, minHeight: 64, resize: "vertical" }} value={metaDescription} onChange={(e) => setMetaDescription(e.target.value)} />
          </div>

          <GalleryEditor product={product} refetch={refetch} message={message} />

          <VariantEditor product={product} refetch={refetch} message={message} />
        </div>

        {/* right column: read-only pricing/stock + editable organization */}
        <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 16 }}>
          <div style={sideCard}>
            <div style={sideTitle}>{t("products:pricingStock")}</div>
            <div>
              <div style={fieldLabel}>{t("products:priceHuf")}</div>
              <div style={readField}>{huf(defaultVariant?.regularPriceHuf ?? null)}</div>
            </div>
            <div>
              <div style={fieldLabel}>{t("products:vat")}</div>
              <div style={readField}>{product.vatRatePercent == null ? "—" : `${product.vatRatePercent}%`}</div>
            </div>
            <div>
              <div style={fieldLabel}>{t("products:totalStock")}</div>
              <div style={{ ...readField, color: "var(--muted)" }}>{totalStock} {t("products:totalStockHint")}</div>
            </div>
          </div>

          <div style={{ ...sideCard, gap: 10 }}>
            <div style={sideTitle}>{t("products:organization")}</div>
            <div>
              <div style={fieldLabel}>{t("products:editCategories")}</div>
              <CategorySelect categories={allCategories} value={categorySlugs} onChange={setCategorySlugs} />
            </div>
            <div>
              <div style={fieldLabel}>{t("products:editStatus")}</div>
              <select style={editField} value={status} onChange={(e) => setStatus(e.target.value as ProductStatus)}>
                <option value="PUBLISHED">{t("products:published")}</option>
                <option value="DRAFT">{t("products:draft")}</option>
              </select>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

// --- Variant price editor component -----------------------------------------
// Renders below each variant row in the editable branch. Provides per-variant
// net/gross amount inputs for regular and sale price, optional sale window, and
// a per-row Save button calling PUT /api/admin/products/{id}/variants/{id}/price.

const toNet = (gross: number, rate: number) => Math.round((gross * 100) / (100 + rate));
const toGross = (net: number, rate: number) => Math.round((net * (100 + rate)) / 100);

interface VariantPriceEditorProps {
  variant: ProductVariantView;
  productId: number;
  vatRate: number;
  refetch: () => void;
  message: ReturnType<typeof App.useApp>["message"];
}

const VariantPriceEditor = ({ variant, productId, vatRate, refetch, message }: VariantPriceEditorProps) => {
  const { t } = useTranslation();

  const [regularAmount, setRegularAmount] = useState<string>(
    variant.regularPriceHuf != null ? String(variant.regularPriceHuf) : "",
  );
  const [regularBasis, setRegularBasis] = useState<PriceBasis>("GROSS");
  const [saleAmount, setSaleAmount] = useState<string>(
    variant.salePriceHuf != null ? String(variant.salePriceHuf) : "",
  );
  const [saleBasis, setSaleBasis] = useState<PriceBasis>("GROSS");
  const [saleFrom, setSaleFrom] = useState<string>("");
  const [saleTo, setSaleTo] = useState<string>("");
  const [busy, setBusy] = useState(false);

  // Reseed if the variant data changes (after a refetch).
  useEffect(() => {
    setRegularAmount(variant.regularPriceHuf != null ? String(variant.regularPriceHuf) : "");
    setRegularBasis("GROSS");
    setSaleAmount(variant.salePriceHuf != null ? String(variant.salePriceHuf) : "");
    setSaleBasis("GROSS");
    setSaleFrom("");
    setSaleTo("");
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [variant.id]);

  const computeHint = (amountStr: string, basis: PriceBasis): string => {
    const n = parseFloat(amountStr);
    if (isNaN(n) || !isFinite(n)) return "—";
    if (basis === "GROSS") {
      return `${toNet(n, vatRate).toLocaleString("hu-HU")} Ft ${t("products:priceNet")}`;
    }
    return `${toGross(n, vatRate).toLocaleString("hu-HU")} Ft ${t("products:priceGross")}`;
  };

  const handleSave = async () => {
    if (busy) return;
    setBusy(true);
    try {
      // Amounts are HUF-forint integers (the backend stores `long`); round explicitly so a
      // fractional entry can't be silently truncated by Jackson.
      const regularNum = Math.round(parseFloat(regularAmount));
      const saleNum = Math.round(parseFloat(saleAmount));

      const sale =
        saleAmount.trim() === "" || isNaN(saleNum) || !isFinite(saleNum) ? null : { amount: saleNum, basis: saleBasis };

      const payload: PriceUpdate = {
        regular: isNaN(regularNum) || !isFinite(regularNum) ? null : { amount: regularNum, basis: regularBasis },
        sale,
        // The sale window is meaningless without a sale price — only send it when there is one.
        saleFrom: sale !== null && saleFrom.trim() !== "" ? new Date(saleFrom).toISOString() : null,
        saleTo: sale !== null && saleTo.trim() !== "" ? new Date(saleTo).toISOString() : null,
      };

      const res = await apiFetch(`${API_BASE}/products/${productId}/variants/${variant.id}/price`, {
        method: "PUT",
        body: JSON.stringify(payload),
      });

      if (res.ok) {
        message.success(t("products:priceSaved"));
        refetch();
      } else {
        message.error(await res.text().catch(() => "..."));
      }
    } finally {
      setBusy(false);
    }
  };

  const basisToggle = (current: PriceBasis, onChange: (b: PriceBasis) => void) => (
    <div style={{ display: "inline-flex", borderRadius: 3, overflow: "hidden", border: "1px solid var(--border)", fontSize: 12 }}>
      {(["NET", "GROSS"] as PriceBasis[]).map((b) => (
        <button
          key={b}
          type="button"
          onClick={() => onChange(b)}
          style={{
            border: 0,
            padding: "4px 10px",
            fontFamily: "inherit",
            fontWeight: 600,
            cursor: "pointer",
            background: current === b ? "var(--accent)" : "var(--surface)",
            color: current === b ? "#fff" : "var(--muted)",
            fontSize: 12,
          }}
        >
          {b === "NET" ? t("products:priceNet") : t("products:priceGross")}
        </button>
      ))}
    </div>
  );

  return (
    <div style={{ padding: "10px 12px 14px", background: "var(--fill)", borderTop: "1px solid var(--border-soft)", display: "flex", flexDirection: "column", gap: 10 }}>
      {/* Regular price row */}
      <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
        <div style={{ fontSize: 12, fontWeight: 600, color: "var(--muted)", width: 80 }}>{t("products:priceRegular")}</div>
        <input
          type="number"
          min={0}
          step={1}
          value={regularAmount}
          onChange={(e) => setRegularAmount(e.target.value)}
          style={{ ...editField, width: 120 }}
          placeholder="Ft"
        />
        {basisToggle(regularBasis, setRegularBasis)}
        <div style={{ fontSize: 12, color: "var(--faint)" }}>{computeHint(regularAmount, regularBasis)}</div>
      </div>

      {/* Sale price row */}
      <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
        <div style={{ fontSize: 12, fontWeight: 600, color: "var(--muted)", width: 80 }}>{t("products:priceSale")}</div>
        <input
          type="number"
          min={0}
          step={1}
          value={saleAmount}
          onChange={(e) => setSaleAmount(e.target.value)}
          style={{ ...editField, width: 120 }}
          placeholder="Ft"
        />
        {basisToggle(saleBasis, setSaleBasis)}
        <div style={{ fontSize: 12, color: "var(--faint)" }}>{saleAmount.trim() !== "" ? computeHint(saleAmount, saleBasis) : ""}</div>
      </div>

      {/* Sale window */}
      <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
        <div style={{ fontSize: 12, fontWeight: 600, color: "var(--muted)", width: 80 }}>{t("products:priceSaleFrom")}</div>
        <input
          type="datetime-local"
          value={saleFrom}
          onChange={(e) => setSaleFrom(e.target.value)}
          style={{ ...editField, width: 200 }}
        />
        <div style={{ fontSize: 12, fontWeight: 600, color: "var(--muted)" }}>{t("products:priceSaleTo")}</div>
        <input
          type="datetime-local"
          value={saleTo}
          onChange={(e) => setSaleTo(e.target.value)}
          style={{ ...editField, width: 200 }}
        />
      </div>

      {/* Save button */}
      <div>
        <button
          type="button"
          disabled={busy}
          style={{ ...smallBtnAccent, cursor: busy ? "wait" : "pointer" }}
          onClick={handleSave}
        >
          {t("products:priceSave")}
        </button>
      </div>
    </div>
  );
};

// --- Variant editor component -----------------------------------------------
// Renders inside the editable branch. Manages create/update/delete/reorder of
// product variants, and per-variant attribute combo pickers with inline add-term.

interface VariantEditorProps {
  product: ProductDetail;
  refetch: () => void;
  message: ReturnType<typeof App.useApp>["message"];
}

const VariantEditor = ({ product, refetch, message }: VariantEditorProps) => {
  const { t } = useTranslation();
  const [attrs, setAttrs] = useState<AttributeView[]>([]);
  const [busy, setBusy] = useState(false);

  // Local per-variant edit state: sku + selected attributeValueId per attributeId
  const [variantSku, setVariantSku] = useState<Record<number, string>>({});
  const [variantAttrSel, setVariantAttrSel] = useState<Record<number, Record<number, number | null>>>({});

  // Add-variant form state
  const [addSku, setAddSku] = useState("");
  const [addAttrSel, setAddAttrSel] = useState<Record<number, number | null>>({});

  const fetchAttrs = async () => {
    try {
      const res = await apiFetch(`${API_BASE}/attributes`);
      if (res.ok) {
        const data: AttributeView[] = await res.json();
        setAttrs(data);
        return data;
      }
    } catch {
      // ignore
    }
    return attrs;
  };

  // Seed local edit state from product variants whenever product changes.
  useEffect(() => {
    const skuMap: Record<number, string> = {};
    const selMap: Record<number, Record<number, number | null>> = {};
    for (const v of product.variants) {
      skuMap[v.id] = v.sku ?? "";
      const attrSel: Record<number, number | null> = {};
      for (const av of (v.attributeValues ?? [])) {
        attrSel[av.attributeId] = av.id;
      }
      selMap[v.id] = attrSel;
    }
    setVariantSku(skuMap);
    setVariantAttrSel(selMap);
  }, [product.id, product.variants]);

  // Fetch the attribute catalog on mount.
  useEffect(() => {
    fetchAttrs();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const withBusy = async (fn: () => Promise<void>) => {
    if (busy) return;
    setBusy(true);
    try {
      await fn();
    } finally {
      setBusy(false);
    }
  };

  const handleAttrSel = (variantId: number, attrId: number, valueId: number | null) => {
    setVariantAttrSel((prev) => ({
      ...prev,
      [variantId]: { ...(prev[variantId] ?? {}), [attrId]: valueId },
    }));
  };

  const handleAddAttrSel = (attrId: number, valueId: number | null) => {
    setAddAttrSel((prev) => ({ ...prev, [attrId]: valueId }));
  };

  const handleAddTerm = async (attr: AttributeView, forVariantId: number | null) => {
    const label = window.prompt(t("products:attrNewTermPrompt"));
    if (!label || !label.trim()) return;
    withBusy(async () => {
      const res = await apiFetch(`${API_BASE}/attributes/${attr.id}/values`, {
        method: "POST",
        body: JSON.stringify({ label: label.trim() }),
      });
      if (res.ok) {
        const updatedAttr: AttributeView = await res.json();
        // Refresh the full catalog and then auto-select the new value
        const updatedAttrs = await fetchAttrs();
        const freshAttr = updatedAttrs.find((a) => a.id === attr.id) ?? updatedAttr;
        const newOption = freshAttr.values.find((v: AttributeValueOption) => v.label === label.trim());
        if (newOption) {
          if (forVariantId !== null) {
            handleAttrSel(forVariantId, attr.id, newOption.id);
          } else {
            handleAddAttrSel(attr.id, newOption.id);
          }
        }
      } else {
        message.error(await res.text().catch(() => "..."));
      }
    });
  };

  const handleSaveVariant = (v: ProductVariantView) => {
    withBusy(async () => {
      const skuVal = (variantSku[v.id] ?? "").trim() || null;
      const sel = variantAttrSel[v.id] ?? {};
      const attributeValueIds = Object.values(sel).filter((id): id is number => id != null);
      const res = await apiFetch(`${API_BASE}/products/${product.id}/variants/${v.id}`, {
        method: "PUT",
        body: JSON.stringify({ sku: skuVal, attributeValueIds }),
      });
      if (res.ok) {
        message.success(t("products:variantSaved"));
        refetch();
      } else {
        message.error(await res.text().catch(() => "..."));
      }
    });
  };

  const handleDeleteVariant = (v: ProductVariantView) => {
    withBusy(async () => {
      const res = await apiFetch(`${API_BASE}/products/${product.id}/variants/${v.id}`, {
        method: "DELETE",
      });
      if (res.ok) {
        message.success(t("products:variantDeleted"));
        refetch();
      } else {
        message.error(await res.text().catch(() => "..."));
      }
    });
  };

  const handleReorder = (index: number, direction: -1 | 1) => {
    const variants = product.variants;
    const newOrder = variants.map((v) => v.id);
    const swapWith = index + direction;
    [newOrder[index], newOrder[swapWith]] = [newOrder[swapWith], newOrder[index]];
    withBusy(async () => {
      const res = await apiFetch(`${API_BASE}/products/${product.id}/variants/reorder`, {
        method: "POST",
        body: JSON.stringify({ variantIds: newOrder }),
      });
      if (res.ok) {
        refetch();
      } else {
        message.error(await res.text().catch(() => "..."));
      }
    });
  };

  const handleAddVariant = () => {
    withBusy(async () => {
      const skuVal = addSku.trim() || null;
      const attributeValueIds = Object.values(addAttrSel).filter((id): id is number => id != null);
      const res = await apiFetch(`${API_BASE}/products/${product.id}/variants`, {
        method: "POST",
        body: JSON.stringify({ sku: skuVal, attributeValueIds }),
      });
      if (res.ok) {
        message.success(t("products:variantCreated"));
        setAddSku("");
        setAddAttrSel({});
        refetch();
      } else {
        message.error(await res.text().catch(() => "..."));
      }
    });
  };

  const renderAttrSelects = (
    currentSel: Record<number, number | null>,
    onSel: (attrId: number, valueId: number | null) => void,
    onAddTerm: (attr: AttributeView) => void,
  ) => (
    <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center" }}>
      {attrs.map((attr) => (
        <div key={attr.id} style={{ display: "flex", alignItems: "center", gap: 4 }}>
          <div style={{ fontSize: 11, color: "var(--muted)", fontWeight: 600 }}>{attr.label}:</div>
          <select
            style={{ ...editField, width: "auto", padding: "4px 6px", fontSize: 12 }}
            value={currentSel[attr.id] ?? ""}
            onChange={(e) => onSel(attr.id, e.target.value ? Number(e.target.value) : null)}
          >
            <option value="">{t("products:attrSelect")}</option>
            {attr.values.map((v) => (
              <option key={v.id} value={v.id}>{v.label}</option>
            ))}
          </select>
          <button
            type="button"
            disabled={busy}
            style={{ ...smallBtn, cursor: busy ? "wait" : "pointer", fontSize: 11 }}
            onClick={() => onAddTerm(attr)}
          >
            {t("products:attrAddTerm")}
          </button>
        </div>
      ))}
    </div>
  );

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", marginBottom: 8 }}>
        <div style={{ fontSize: 13, fontWeight: 700 }}>{t("products:variants")}</div>
        <div style={{ flex: 1 }} />
        <div style={{ fontSize: 12, color: "var(--muted)" }}>{t("products:variantsHint")}</div>
      </div>
      <div style={{ fontSize: 12, color: "var(--muted)", marginBottom: 10 }}>
        {t("products:priceVatRate")}: {product.effectiveVatRatePercent ?? product.vatRatePercent ?? "—"}%
      </div>

      {/* Existing variants */}
      <div style={{ border: "1px solid var(--border)", borderRadius: 3, overflow: "hidden", marginBottom: 16 }}>
        <div style={{ display: "flex", fontSize: 11, color: "var(--faint)", fontWeight: 600, padding: "8px 12px", gap: 12, background: "var(--th)", borderBottom: "1px solid var(--border-soft)" }}>
          <div style={{ flex: 1 }}>{t("products:colVariant")}</div>
          <div style={{ width: 96 }}>{t("products:sku")}</div>
          <div style={{ width: 56, textAlign: "right" }}>{t("products:stock")}</div>
        </div>
        {product.variants.map((v, i) => (
          <div key={v.id} style={{ borderBottom: i < product.variants.length - 1 ? "1px solid var(--border-soft)" : undefined }}>
            {/* Read-only summary row */}
            <div style={{ display: "flex", fontSize: 13, padding: "9px 12px", gap: 12, alignItems: "center" }}>
              <div style={{ flex: 1 }}>{v.label}</div>
              <div style={{ width: 96, color: "var(--muted)", fontFamily: "monospace", fontSize: 12 }}>{v.sku || t("products:variantNone")}</div>
              <div style={{ width: 56, textAlign: "right" }}>{v.stockQty}</div>
            </div>

            {/* Variant edit controls */}
            <div style={{ padding: "10px 12px 14px", background: "var(--fill)", borderTop: "1px solid var(--border-soft)", display: "flex", flexDirection: "column", gap: 10 }}>
              {/* Attribute selects */}
              {attrs.length > 0 && renderAttrSelects(
                variantAttrSel[v.id] ?? {},
                (attrId, valueId) => handleAttrSel(v.id, attrId, valueId),
                (attr) => handleAddTerm(attr, v.id),
              )}

              {/* SKU + action buttons row */}
              <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                <div style={{ fontSize: 12, fontWeight: 600, color: "var(--muted)" }}>{t("products:variantSku")}:</div>
                <input
                  style={{ ...editField, width: 140 }}
                  value={variantSku[v.id] ?? ""}
                  onChange={(e) => setVariantSku((prev) => ({ ...prev, [v.id]: e.target.value }))}
                  placeholder={t("products:variantNone")}
                />
                <button
                  type="button"
                  disabled={busy}
                  style={{ ...smallBtnAccent, cursor: busy ? "wait" : "pointer" }}
                  onClick={() => handleSaveVariant(v)}
                >
                  {t("products:variantSave")}
                </button>
                <button
                  type="button"
                  disabled={busy}
                  style={{ ...smallBtn, color: "#C0392B", borderColor: "#C0392B", cursor: busy ? "wait" : "pointer" }}
                  onClick={() => handleDeleteVariant(v)}
                >
                  {t("products:variantDelete")}
                </button>
                <button
                  type="button"
                  disabled={busy || i === 0}
                  style={{ ...smallBtn, cursor: busy || i === 0 ? "not-allowed" : "pointer", opacity: i === 0 ? 0.35 : 1 }}
                  onClick={() => handleReorder(i, -1)}
                >
                  {t("products:variantMoveUp")}
                </button>
                <button
                  type="button"
                  disabled={busy || i === product.variants.length - 1}
                  style={{ ...smallBtn, cursor: busy || i === product.variants.length - 1 ? "not-allowed" : "pointer", opacity: i === product.variants.length - 1 ? 0.35 : 1 }}
                  onClick={() => handleReorder(i, 1)}
                >
                  {t("products:variantMoveDown")}
                </button>
              </div>
            </div>

            {/* Price editor (P2b-1) */}
            <VariantPriceEditor
              variant={v}
              productId={product.id}
              vatRate={product.effectiveVatRatePercent ?? product.vatRatePercent ?? 0}
              refetch={refetch}
              message={message}
            />
          </div>
        ))}
      </div>

      {/* Add variant form */}
      <div style={{ border: "1px solid var(--border)", borderRadius: 3, padding: "14px 12px", background: "var(--fill)", display: "flex", flexDirection: "column", gap: 10 }}>
        <div style={{ fontSize: 13, fontWeight: 700 }}>{t("products:variantAdd")}</div>
        {attrs.length > 0 && renderAttrSelects(
          addAttrSel,
          (attrId, valueId) => handleAddAttrSel(attrId, valueId),
          (attr) => handleAddTerm(attr, null),
        )}
        <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
          <div style={{ fontSize: 12, fontWeight: 600, color: "var(--muted)" }}>{t("products:variantSku")}:</div>
          <input
            style={{ ...editField, width: 140 }}
            value={addSku}
            onChange={(e) => setAddSku(e.target.value)}
            placeholder={t("products:variantNone")}
          />
          <button
            type="button"
            disabled={busy}
            style={{ ...accentBtn, cursor: busy ? "wait" : "pointer" }}
            onClick={handleAddVariant}
          >
            {t("products:variantAdd")}
          </button>
        </div>
      </div>
    </div>
  );
};

// --- Gallery editor component -----------------------------------------------
// Renders inside the editable branch only. Provides upload, delete, set-cover,
// and reorder (◀/▶) controls for product images.

const GalleryEditor = ({ product, refetch, message }: EditableProps) => {
  const { t } = useTranslation();
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);

  const withBusy = async (fn: () => Promise<void>) => {
    if (busy) return;
    setBusy(true);
    try {
      await fn();
    } finally {
      setBusy(false);
    }
  };

  const handleUpload = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    // Reset input so the same file can be re-selected.
    e.target.value = "";
    withBusy(async () => {
      const fd = new FormData();
      fd.append("file", file);
      const res = await apiFetch(`${API_BASE}/products/${product.id}/images`, { method: "POST", body: fd });
      if (res.ok) {
        message.success(t("products:imgUploaded"));
        refetch();
      } else {
        message.error(await res.text().catch(() => t("products:imgUploadFailed")));
      }
    });
  };

  const handleDelete = (imgId: number) => {
    withBusy(async () => {
      const res = await apiFetch(`${API_BASE}/products/${product.id}/images/${imgId}`, { method: "DELETE" });
      if (res.ok) {
        message.success(t("products:imgDeleted"));
        refetch();
      } else {
        message.error(await res.text().catch(() => t("products:imgDeleteFailed")));
      }
    });
  };

  const handleSetCover = (imgId: number) => {
    withBusy(async () => {
      const res = await apiFetch(`${API_BASE}/products/${product.id}/images/${imgId}/cover`, { method: "POST" });
      if (res.ok) {
        message.success(t("products:imgCoverSet"));
        refetch();
      } else {
        message.error(await res.text().catch(() => t("products:imgCoverFailed")));
      }
    });
  };

  const handleMove = (index: number, direction: -1 | 1) => {
    const images = product.images;
    const newOrder = images.map((img) => img.id);
    const swapWith = index + direction;
    [newOrder[index], newOrder[swapWith]] = [newOrder[swapWith], newOrder[index]];
    withBusy(async () => {
      const res = await apiFetch(`${API_BASE}/products/${product.id}/images/reorder`, {
        method: "POST",
        body: JSON.stringify({ imageIds: newOrder }),
      });
      if (res.ok) {
        message.success(t("products:imgReordered"));
        refetch();
      } else {
        message.error(await res.text().catch(() => t("products:imgReorderFailed")));
      }
    });
  };

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", marginBottom: 10, gap: 10 }}>
        <div style={fieldLabel}>{t("products:images")}</div>
        <div style={{ flex: 1 }} />
        <input
          ref={fileRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          style={{ display: "none" }}
          onChange={handleUpload}
        />
        <button
          type="button"
          disabled={busy}
          style={{ ...smallBtnAccent, cursor: busy ? "wait" : "pointer" }}
          onClick={() => fileRef.current?.click()}
        >
          {t("products:imgUpload")}
        </button>
      </div>

      {product.images.length > 0 ? (
        <div style={{ display: "flex", gap: 12, alignItems: "flex-start", flexWrap: "wrap" }}>
          {product.images.map((img, i) => (
            <div key={img.id} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}>
              <div style={{ width: i === 0 ? 104 : 72, height: i === 0 ? 104 : 72, borderRadius: 3, background: "var(--fill)", border: "1px solid var(--border)", position: "relative", overflow: "hidden" }}>
                <img src={img.url} alt={img.alt ?? product.name} style={{ width: "100%", height: "100%", objectFit: "cover", display: "block" }} />
                {i === 0 && (
                  <span style={{ position: "absolute", top: 5, left: 5, background: "var(--accent)", color: "#fff", fontSize: 10, fontWeight: 600, borderRadius: 3, padding: "1px 6px" }}>
                    {t("products:cover")}
                  </span>
                )}
              </div>
              <div style={{ display: "flex", gap: 4, flexWrap: "wrap", justifyContent: "center" }}>
                <button
                  type="button"
                  disabled={busy || i === 0}
                  style={{ ...smallBtn, cursor: busy || i === 0 ? "not-allowed" : "pointer", opacity: i === 0 ? 0.35 : 1 }}
                  onClick={() => handleMove(i, -1)}
                  title={t("products:imgMoveLeft")}
                >
                  {t("products:imgMoveLeft")}
                </button>
                <button
                  type="button"
                  disabled={busy || i === product.images.length - 1}
                  style={{ ...smallBtn, cursor: busy || i === product.images.length - 1 ? "not-allowed" : "pointer", opacity: i === product.images.length - 1 ? 0.35 : 1 }}
                  onClick={() => handleMove(i, 1)}
                  title={t("products:imgMoveRight")}
                >
                  {t("products:imgMoveRight")}
                </button>
                {i !== 0 && (
                  <button
                    type="button"
                    disabled={busy}
                    style={{ ...smallBtnAccent, cursor: busy ? "wait" : "pointer" }}
                    onClick={() => handleSetCover(img.id)}
                  >
                    {t("products:imgMakeCover")}
                  </button>
                )}
                <button
                  type="button"
                  disabled={busy}
                  style={{ ...smallBtn, color: "#C0392B", borderColor: "#C0392B", cursor: busy ? "wait" : "pointer" }}
                  onClick={() => handleDelete(img.id)}
                >
                  {t("products:imgDelete")}
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div style={{ display: "flex", gap: 10, alignItems: "flex-start" }}>
          <div style={{ width: 104, height: 104, borderRadius: 3, background: "var(--fill)", border: "1px solid var(--border)" }} />
          <div style={{ width: 72, height: 72, borderRadius: 3, background: "var(--fill)", border: "1px solid var(--border)" }} />
          <div style={{ width: 72, height: 72, borderRadius: 3, background: "var(--fill)", border: "1px solid var(--border)" }} />
        </div>
      )}
    </div>
  );
};
