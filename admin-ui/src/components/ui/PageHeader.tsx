import type { ReactNode } from "react";
export function PageHeader({ title, actions, back }: { title: string; actions?: ReactNode; back?: ReactNode }) {
  return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 18 }}>
      <div>
        {back}
        <h1 className="page-title" style={{ fontSize: 24, fontWeight: 700, margin: 0 }}>{title}</h1>
      </div>
      <div style={{ display: "flex", gap: 8 }}>{actions}</div>
    </div>
  );
}
