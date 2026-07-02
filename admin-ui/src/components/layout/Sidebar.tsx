import { useEffect, useState, type CSSProperties } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { NAV, type NavRow } from "./nav.config";
import { activeKey, expandedParent } from "./navState";

// Faithful port of the prototype sidebar (Admin Mid-fi HU.dc.html lines 38-45 +
// the nav-building logic at 941-976): exact spacing, the inset accent bar on the
// active row, accent-soft fill, prototype badges, and the indented child dots.

export function Sidebar({
  collapsed,
  navOpen,
  onToggleCollapse,
  onNavigate,
}: {
  collapsed: boolean;
  navOpen: boolean;
  onToggleCollapse: () => void;
  onNavigate: () => void;
}) {
  const nav = useNavigate();
  const { pathname } = useLocation();
  const { t } = useTranslation();
  const active = activeKey(pathname);
  const [expanded, setExpanded] = useState<string | null>(expandedParent(pathname));
  useEffect(() => setExpanded(expandedParent(pathname)), [pathname]);

  const go = (route: string) => {
    nav(route);
    onNavigate();
  };

  const sidebarStyle: CSSProperties = {
    width: collapsed ? 68 : 234,
    flex: "none",
    background: "var(--surface)",
    borderRight: "1px solid var(--border)",
    padding: collapsed ? "18px 10px" : "18px 14px",
    display: "flex",
    flexDirection: "column",
    gap: 3,
    height: "100vh",
    overflowY: "auto",
  };

  const renderRow = ({ item }: NavRow) => {
    const sectionActive = active === item.route || !!item.children?.some((c) => c.route === active);
    const anyChildActive = !!item.children?.some((c) => c.route !== item.route && active === c.route);
    const hasChildren = !!item.children;
    // `expanded` holds the active parent's ROUTE (what expandedParent returns and
    // what the [pathname] effect re-seeds), so compare against route — not id —
    // or the submenu would collapse on every navigation.
    const isExp = expanded === item.route;

    const rowStyle: CSSProperties = {
      display: "flex",
      alignItems: "center",
      gap: collapsed ? 0 : 11,
      justifyContent: collapsed ? "center" : "flex-start",
      padding: collapsed ? "10px 0" : "9px 11px",
      borderRadius: 3,
      fontSize: 14,
      cursor: "pointer",
      fontWeight: sectionActive ? 600 : 500,
      marginTop: item.bottom ? "auto" : 0,
      background: sectionActive && !anyChildActive ? "var(--accent-soft)" : "transparent",
      color: sectionActive ? "var(--accent-fg)" : "var(--muted)",
      boxShadow: sectionActive ? "inset 3px 0 0 var(--accent)" : "none",
    };

    const onClick = () => {
      if (hasChildren) {
        go(item.route);
        setExpanded(isExp ? null : item.route);
      } else {
        go(item.route);
        setExpanded(null);
      }
    };

    return (
      <div key={item.id} style={item.bottom ? { marginTop: "auto" } : undefined}>
        <div style={rowStyle} onClick={onClick}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" style={{ flex: "none", opacity: 0.82 }}>
            <path d={item.iconPath} />
          </svg>
          {!collapsed && (
            <>
              <span style={{ flex: 1 }}>{t(item.labelKey)}</span>
              {item.badge && (
                <span style={{ background: "var(--accent)", color: "#fff", fontSize: 10, fontWeight: 700, borderRadius: 999, padding: "1px 7px", minWidth: 18, textAlign: "center" }}>
                  {item.badge}
                </span>
              )}
              {/* Always render the caret slot (empty when no children) so its fixed
                  11px width reserves space and every badge aligns vertically —
                  matches the prototype (Admin Mid-fi HU.dc.html line 44). */}
              <span style={{ fontSize: 10, opacity: 0.55, width: 11, textAlign: "center", flex: "none" }}>{hasChildren ? (isExp ? "▾" : "▸") : ""}</span>
            </>
          )}
        </div>
        {hasChildren && isExp && !collapsed &&
          item.children!.map((c) => {
            const ca = active === c.route;
            return (
              <div
                key={c.route}
                onClick={() => go(c.route)}
                style={{ display: "flex", alignItems: "center", gap: 10, padding: "6px 11px 6px 22px", borderRadius: 3, fontSize: 13, cursor: "pointer", fontWeight: ca ? 600 : 500, background: ca ? "var(--accent-soft)" : "transparent", color: ca ? "var(--accent-fg)" : "var(--muted)" }}
              >
                <span style={{ width: 5, height: 5, borderRadius: "50%", background: "currentColor", opacity: ca ? 0.95 : 0.45, flex: "none", marginLeft: 6 }} />
                <span style={{ flex: 1 }}>{t(c.labelKey)}</span>
              </div>
            );
          })}
      </div>
    );
  };

  // Section headers and the bottom-pinned Settings need to sit in the same flex
  // column so `margin-top:auto` pushes Settings down (prototype behavior).
  return (
    <div data-role="sidebar" data-open={navOpen ? "true" : "false"} style={sidebarStyle}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 10, padding: "2px 4px 12px" }}>
        {collapsed ? (
          // The logo PNGs are transparent black ink; themeVars.css inverts them to
          // white ink in dark mode (data-brandlogo target). No background needed.
          <img
            data-brandlogo
            src="/admin/logo-small.png"
            alt="n"
            style={{ width: 38, height: 38, objectFit: "contain", flex: "none" }}
          />
        ) : (
          <div style={{ display: "flex", flexDirection: "column", flex: 1, minWidth: 0 }}>
            <img
              data-brandlogo
              src="/admin/logo.png"
              alt="nemiskacat"
              style={{ width: "100%", height: "auto", display: "block" }}
            />
            <span style={{ fontSize: 11, color: "var(--faint)", fontWeight: 600, letterSpacing: ".4px", marginTop: 3 }}>admin</span>
          </div>
        )}
        {!collapsed && (
          <div onClick={onToggleCollapse} style={{ cursor: "pointer", color: "var(--faint)", fontSize: 16, padding: "2px 4px", marginLeft: "auto" }}>«</div>
        )}
      </div>
      {collapsed && (
        <div onClick={onToggleCollapse} style={{ cursor: "pointer", color: "var(--faint)", fontSize: 16, textAlign: "center", padding: "2px 0 10px" }}>»</div>
      )}
      {NAV.map((row) => (
        <div key={row.item.id} style={{ display: "contents" }}>
          {!collapsed && row.headerKey && (
            <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: "1px", textTransform: "uppercase", color: "var(--faint)", padding: "14px 11px 5px" }}>{t(row.headerKey)}</div>
          )}
          {renderRow(row)}
        </div>
      ))}
    </div>
  );
}
