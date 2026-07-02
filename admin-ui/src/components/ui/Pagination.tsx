import type { CSSProperties } from "react";

// Faithful port of the customers-screen pagination (Admin Mid-fi HU.dc.html line 444):
// "from–to / total" + ‹ [numbered pages with ellipsis, active highlighted] ›.
const cell: CSSProperties = { border: "1px solid var(--border)", borderRadius: 2, padding: "5px 11px", cursor: "pointer" };
const arrow: CSSProperties = { border: "1px solid var(--border)", borderRadius: 2, padding: "5px 10px", cursor: "pointer" };
const active: CSSProperties = { border: "1px solid var(--accent)", background: "var(--accent-soft)", color: "var(--accent-fg)", fontWeight: 600, borderRadius: 2, padding: "5px 11px", cursor: "pointer" };

function pageList(current: number, totalPages: number): (number | "…")[] {
  const want = new Set<number>([1, 2, 3, current - 1, current, current + 1, totalPages]);
  const sorted = [...want].filter((p) => p >= 1 && p <= totalPages).sort((a, b) => a - b);
  const out: (number | "…")[] = [];
  let prev = 0;
  for (const p of sorted) {
    if (prev && p - prev > 1) out.push("…");
    out.push(p);
    prev = p;
  }
  return out;
}

export function Pagination({
  current,
  pageSize,
  total,
  onChange,
}: {
  current: number;
  pageSize: number;
  total: number;
  onChange: (page: number) => void;
}) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const from = total === 0 ? 0 : (current - 1) * pageSize + 1;
  const to = Math.min(current * pageSize, total);
  return (
    <div style={{ padding: "12px 18px", fontSize: 13, color: "var(--muted)", display: "flex", alignItems: "center", gap: 6, borderTop: "1px solid var(--border-soft)" }}>
      {from}–{to} / {total.toLocaleString("hu-HU")}
      <span style={{ flex: 1 }} />
      <span style={arrow} onClick={() => onChange(Math.max(1, current - 1))}>‹</span>
      {pageList(current, totalPages).map((p, i) =>
        p === "…" ? (
          <span key={`e${i}`} style={{ color: "var(--muted)", padding: "0 4px" }}>…</span>
        ) : (
          <span key={p} style={p === current ? active : cell} onClick={() => onChange(p)}>{p}</span>
        ),
      )}
      <span style={arrow} onClick={() => onChange(Math.min(totalPages, current + 1))}>›</span>
    </div>
  );
}
