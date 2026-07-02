import { useState, type ReactNode } from "react";
import { Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";

// Faithful port of the prototype shell (Admin Mid-fi HU.dc.html lines 35-75):
// full-height flex with the sidebar, a mobile backdrop, the 62px top bar, and a
// scrolling content area (24px 26px padding, 1760px centered). Responsive
// sidebar/hamburger/backdrop behavior is driven by the data-role @media rules in
// themeVars.css (matching the prototype), so no width listener is needed.

export function AdminLayout({ children }: { children: ReactNode }) {
  const [collapsed, setCollapsed] = useState(false);
  const [navOpen, setNavOpen] = useState(false);

  return (
    <div style={{ height: "100vh", width: "100%", display: "flex", overflow: "hidden", background: "var(--bg)", fontFamily: "'Hanken Grotesk',sans-serif", color: "var(--text)" }}>
      <Sidebar
        collapsed={collapsed}
        navOpen={navOpen}
        onToggleCollapse={() => setCollapsed((c) => !c)}
        onNavigate={() => setNavOpen(false)}
      />
      <div
        data-role="backdrop"
        data-open={navOpen ? "true" : "false"}
        onClick={() => setNavOpen(false)}
        style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,.4)", zIndex: 65, display: "none" }}
      />
      <div style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
        <Topbar onHamburger={() => setNavOpen((o) => !o)} />
        <div style={{ flex: 1, overflow: "auto", padding: "24px 26px" }}>
          <div style={{ maxWidth: 1760, margin: "0 auto" }}>{children}</div>
        </div>
      </div>
    </div>
  );
}
