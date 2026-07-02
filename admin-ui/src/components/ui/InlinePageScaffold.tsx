import type { ReactNode } from "react";
import { PageHeader } from "./PageHeader";
export function InlinePageScaffold({ title, back, actions, children }: { title: string; back?: ReactNode; actions?: ReactNode; children: ReactNode }) {
  return (
    <div style={{ maxWidth: 880 }}>
      <PageHeader title={title} back={back} actions={actions} />
      <div style={{ background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 3, padding: 18 }}>
        {children}
      </div>
    </div>
  );
}
