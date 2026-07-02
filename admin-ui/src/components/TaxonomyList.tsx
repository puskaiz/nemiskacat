import { useList } from "@refinedev/core";
import { useState, type CSSProperties } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Pagination } from "./ui/Pagination";
import type { Taxonomy } from "../types";

interface Props {
  resource: string;
  titleKey: string;
  basePath: string;
  /** i18n key for the "+ New …" button; defaults to the generic "taxonomy:add". */
  addLabelKey?: string;
}

const PAGE_SIZE = 10;

const card: CSSProperties = {
  border: "1px solid var(--border)",
  borderRadius: 2,
  background: "var(--surface)",
  overflow: "hidden",
  boxShadow: "0 1px 2px rgba(16,24,40,.04)",
};

const accentPill: CSSProperties = {
  borderRadius: 999,
  padding: "9px 18px",
  fontSize: 13,
  fontWeight: 600,
  color: "#fff",
  background: "var(--accent)",
  boxShadow: "0 2px 6px rgba(22,24,29,.3)",
  cursor: "pointer",
};

export function TaxonomyList({ resource, titleKey, basePath, addLabelKey = "taxonomy:add" }: Props) {
  const { t } = useTranslation();
  const nav = useNavigate();
  const [current, setCurrent] = useState(1);

  const { data, isLoading } = useList<Taxonomy>({
    resource,
    pagination: { mode: "off" },
  });

  const allRows = [...(data?.data ?? [])].sort((a, b) =>
    a.name.localeCompare(b.name, "hu"),
  );
  const total = allRows.length;
  // Clamp the page so a shrunk list (e.g. after a delete) never lands on a
  // now-empty page and renders the empty state while items still exist.
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const page = Math.min(current, totalPages);
  const rows = allRows.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      {/* header */}
      <div style={{ display: "flex", alignItems: "flex-end", gap: 14 }}>
        <div style={{ fontSize: 24, fontWeight: 700, letterSpacing: "-.3px" }}>
          {t(titleKey)}
        </div>
        <div style={{ flex: 1 }} />
        <div
          style={accentPill}
          onClick={() => nav(`${basePath}/create`)}
        >
          + {t(addLabelKey)}
        </div>
      </div>

      {/* table */}
      <div data-table style={card}>
        <div
          style={{
            display: "flex",
            fontSize: 12,
            color: "var(--faint)",
            padding: "12px 18px",
            gap: 16,
            borderBottom: "1px solid var(--border-soft)",
            fontWeight: 600,
            background: "var(--th)",
          }}
        >
          <div style={{ flex: 1 }}>{t("taxonomy:colName")}</div>
          <div style={{ width: 240 }}>{t("taxonomy:colSlug")}</div>
        </div>

        {!isLoading && rows.length === 0 && (
          <div
            style={{
              padding: "32px 18px",
              textAlign: "center",
              color: "var(--muted)",
              fontSize: 14,
            }}
          >
            {t("taxonomy:empty")}
          </div>
        )}

        {rows.map((row) => (
          <div
            key={row.id}
            onClick={() => nav(`${basePath}/edit/${row.id}`)}
            style={{
              display: "flex",
              fontSize: 14,
              padding: "11px 18px",
              gap: 16,
              borderBottom: "1px solid var(--border-soft)",
              alignItems: "center",
              cursor: "pointer",
            }}
          >
            <div style={{ flex: 1, fontWeight: 600 }}>{row.name}</div>
            <div style={{ width: 240, color: "var(--muted)" }}>{row.slug}</div>
          </div>
        ))}

        <Pagination
          current={page}
          pageSize={PAGE_SIZE}
          total={total}
          onChange={(p) => {
            setCurrent(p);
          }}
        />
      </div>
    </div>
  );
}
