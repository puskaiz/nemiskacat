import { useState, useEffect, useRef, type CSSProperties, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useGetIdentity, useLogout } from "@refinedev/core";
import { useThemeMode } from "../../theme/ThemeProvider";
import { i18n } from "../../i18n";

// Faithful port of the prototype top bar (Admin Mid-fi HU.dc.html lines 51-73):
// 62px bar, faux search field, HU/EN chip toggle, theme + notification boxes, and
// the profile dropdown with the prototype's exact stroke-SVG icons.

const box: CSSProperties = {
  width: 40, height: 40, border: "1px solid var(--border)", borderRadius: 3,
  display: "flex", alignItems: "center", justifyContent: "center", color: "var(--muted)",
  fontSize: 15, cursor: "pointer", flex: "none",
};

function MenuItem({ icon, label, onClick }: { icon: ReactNode; label: string; onClick?: () => void }) {
  return (
    <div onClick={onClick} style={{ padding: "9px 14px", fontSize: 13, cursor: "pointer", display: "flex", alignItems: "center", gap: 10, color: "var(--muted)" }}>
      {icon}
      <span style={{ flex: 1 }}>{label}</span>
    </div>
  );
}

export function Topbar({ onHamburger }: { onHamburger: () => void }) {
  const { t } = useTranslation();
  const { mode, toggle } = useThemeMode();
  const { mutate: logout } = useLogout();
  const { data: identity } = useGetIdentity<{ name?: string; email?: string }>();
  const [profileOpen, setProfileOpen] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);

  // Close on outside click or Escape while dropdown is open.
  useEffect(() => {
    if (!profileOpen) return;

    const handleMouseDown = (e: MouseEvent) => {
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) {
        setProfileOpen(false);
      }
    };
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") setProfileOpen(false);
    };

    document.addEventListener("mousedown", handleMouseDown);
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handleMouseDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [profileOpen]);

  const lang = i18n.language === "en" ? "en" : "hu";
  const name = identity?.name ?? "Anna Kovács";
  const email = identity?.email ?? "anna@nemiskacat.hu";

  const langChip = (code: "hu" | "en") =>
    code === lang
      ? { padding: "4px 10px", background: "var(--surface)", borderRadius: 3, fontWeight: 600, boxShadow: "0 1px 2px rgba(16,24,40,.08)" }
      : { padding: "4px 10px", color: "var(--muted)", cursor: "pointer" };

  return (
    <div style={{ height: 62, flex: "none", borderBottom: "1px solid var(--border)", display: "flex", alignItems: "center", gap: 14, padding: "0 18px 0 26px", background: "var(--surface)" }}>
      <div data-role="hamburger" onClick={onHamburger} style={{ width: 40, height: 40, border: "1px solid var(--border)", borderRadius: 3, alignItems: "center", justifyContent: "center", color: "var(--muted)", fontSize: 18, cursor: "pointer", flex: "none" }}>≡</div>

      <div style={{ flex: 1, maxWidth: 380, height: 40, border: "1px solid var(--border)", borderRadius: 3, display: "flex", alignItems: "center", padding: "0 13px", gap: 9, fontSize: 13, color: "var(--faint)", background: "var(--input)" }}>
        <span style={{ width: 14, height: 14, border: "1.7px solid #b3bcc7", borderRadius: "50%", flex: "none" }} />
        {t("searchLong")}
      </div>

      <div style={{ flex: 1 }} />

      <div style={{ display: "flex", gap: 2, fontSize: 13, background: "var(--fill)", borderRadius: 3, padding: 3 }}>
        <span style={langChip("hu")} onClick={() => i18n.changeLanguage("hu")}>HU</span>
        <span style={langChip("en")} onClick={() => i18n.changeLanguage("en")}>EN</span>
      </div>

      <div onClick={toggle} style={box}>{mode === "dark" ? "☀" : "☾"}</div>

      <div style={{ ...box, color: "var(--faint)" }}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d="M18 8a6 6 0 00-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.7 21a2 2 0 01-3.4 0" /></svg>
      </div>

      <div ref={profileRef} style={{ position: "relative" }}>
        <div onClick={() => setProfileOpen((o) => !o)} style={{ display: "flex", alignItems: "center", gap: 9, cursor: "pointer", paddingLeft: 12, marginLeft: 4, borderLeft: "1px solid var(--border)", flex: "none", whiteSpace: "nowrap" }}>
          <span style={{ width: 34, height: 34, borderRadius: "50%", background: "var(--accent-soft)", color: "var(--accent-fg)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 13, fontWeight: 700, flex: "none" }}>{name.charAt(0)}</span>
          <div style={{ lineHeight: 1.15, whiteSpace: "nowrap" }}>
            <div style={{ fontSize: 13, fontWeight: 600 }}>{name}</div>
            <div style={{ fontSize: 11, color: "var(--faint)" }}>Admin</div>
          </div>
          <span style={{ color: "var(--faint)", fontSize: 11 }}>▾</span>
        </div>
        {profileOpen && (
          <div style={{ position: "absolute", top: 50, right: 0, zIndex: 60, width: 230, background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 3, boxShadow: "0 8px 24px rgba(16,24,40,.18)", overflow: "hidden" }}>
            <div style={{ padding: "12px 14px", borderBottom: "1px solid var(--border-soft)" }}>
              <div style={{ fontSize: 13, fontWeight: 600 }}>{name}</div>
              <div style={{ fontSize: 12, color: "var(--muted)" }}>{email}</div>
            </div>
            <div style={{ padding: "6px 0", borderBottom: "1px solid var(--border-soft)" }}>
              <MenuItem label={t("profileSettings")} icon={<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" style={{ flex: "none", opacity: 0.7 }}><circle cx="12" cy="8" r="4" /><path d="M4 21v-1a6 6 0 016-6h4a6 6 0 016 6v1" /></svg>} />
              <MenuItem label={t("changePassword")} icon={<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" style={{ flex: "none", opacity: 0.7 }}><rect x="5" y="11" width="14" height="9" rx="1" /><path d="M8 11V8a4 4 0 018 0v3" /></svg>} />
              <MenuItem label={t("addPasskey")} icon={<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" style={{ flex: "none", opacity: 0.7 }}><path d="M12 3l7 3v5c0 4-3 7-7 9-4-2-7-5-7-9V6z" /><path d="M9 12l2 2 4-4" /></svg>} />
              <MenuItem label={t("notificationsMenu")} icon={<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" style={{ flex: "none", opacity: 0.7 }}><path d="M18 8a6 6 0 00-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.7 21a2 2 0 01-3.4 0" /></svg>} />
            </div>
            <div style={{ padding: "10px 14px" }}>
              <div onClick={() => logout()} style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 8, borderRadius: 999, padding: 9, fontSize: 13, fontWeight: 600, color: "#fff", background: "#DC4040", cursor: "pointer" }}>
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" style={{ flex: "none" }}><path d="M9 21H5a1 1 0 01-1-1V4a1 1 0 011-1h4 M16 17l5-5-5-5 M21 12H9" /></svg>
                {t("signOut")}
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
