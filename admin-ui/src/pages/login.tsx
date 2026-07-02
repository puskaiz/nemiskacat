import { useState, type CSSProperties } from "react";
import { useLogin } from "@refinedev/core";
import { useTranslation } from "react-i18next";

// Faithful port of the prototype login (Admin Mid-fi HU.dc.html lines 725-735):
// centered 380px card, logo, real email/password inputs styled like the
// prototype's field boxes, accent pill submit, and the passkey button. Wired to
// the real authProvider via useLogin. (Reset / add-passkey are a later pass.)

interface LoginVars { email: string; password: string }

const field: CSSProperties = { border: "1px solid var(--border)", borderRadius: 3, padding: "11px 13px", fontSize: 14, background: "var(--input)", color: "var(--text)", width: "100%", outline: "none", fontFamily: "inherit" };
const label: CSSProperties = { fontSize: 12, color: "var(--muted)", fontWeight: 600, marginBottom: 6 };

export const Login = () => {
  const { t } = useTranslation();
  const { mutate: login, isLoading } = useLogin<LoginVars>();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  return (
    <div style={{ position: "fixed", inset: 0, zIndex: 100, background: "var(--bg)", display: "flex", alignItems: "center", justifyContent: "center", padding: 24, fontFamily: "'Hanken Grotesk',sans-serif", color: "var(--text)" }}>
      <form
        onSubmit={(e) => { e.preventDefault(); login({ email, password }); }}
        style={{ width: 380, background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 3, boxShadow: "0 24px 60px rgba(16,24,40,.18)", padding: 32, display: "flex", flexDirection: "column", gap: 18 }}
      >
        <div style={{ textAlign: "center" }}>
          <div style={{ fontSize: 19, fontWeight: 700 }}>{t("auth:title")}</div>
          <div style={{ fontSize: 13, color: "var(--muted)", marginTop: 3 }}>{t("auth:subtitle")}</div>
        </div>

        <div>
          <div style={label}>{t("auth:email")}</div>
          <input style={field} type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>

        <div>
          <div style={{ display: "flex", alignItems: "center", marginBottom: 6 }}>
            <div style={{ fontSize: 12, color: "var(--muted)", fontWeight: 600 }}>{t("auth:password")}</div>
            <div style={{ flex: 1 }} />
            <div style={{ fontSize: 12, color: "var(--accent-fg)", fontWeight: 600, cursor: "pointer" }}>{t("auth:forgot")}</div>
          </div>
          <input style={field} type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>

        <button type="submit" disabled={isLoading} style={{ border: 0, borderRadius: 999, padding: 11, textAlign: "center", fontSize: 14, fontWeight: 600, color: "#fff", background: "var(--accent)", cursor: isLoading ? "wait" : "pointer", fontFamily: "inherit" }}>
          {t("auth:signIn")}
        </button>

        <div style={{ border: "1px solid var(--border)", borderRadius: 999, padding: 11, textAlign: "center", fontSize: 14, fontWeight: 600, cursor: "not-allowed", opacity: 0.6, display: "flex", alignItems: "center", justifyContent: "center", gap: 8, color: "var(--muted)" }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d="M12 3l7 3v5c0 4-3 7-7 9-4-2-7-5-7-9V6z" /><path d="M9 12l2 2 4-4" /></svg>
          {t("auth:passkey")}
        </div>
      </form>
    </div>
  );
};
