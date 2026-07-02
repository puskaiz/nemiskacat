import { useState, type CSSProperties, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { StatusPill } from "../../components/ui/StatusPill";
import { SETTINGS_TABS, GENERAL_DEFAULTS, TEAM, NOTIF_PREFS } from "../../data/settings";

// Faithful 1:1 port of the prototype Settings screen (Admin Mid-fi HU.dc.html,
// sec.settings block, lines 615-718): left settings sub-nav + the active tab's
// content panel. The active tab is driven by the `tab` prop so the
// /settings/:tab route keeps working; it defaults to the prototype default
// ("team", the sc-if with hint-placeholder-val=true). Static example data.
// The "invite team member" detail sub-view (sc-if detailCreate, lines 616-627)
// is intentionally out of scope.

const PROTO_DEFAULT_TAB = "team";

const card: CSSProperties = { background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 2, boxShadow: "0 1px 2px rgba(16,24,40,.04)" };
const sectionTitle: CSSProperties = { fontSize: 22, fontWeight: 700, letterSpacing: "-.3px" };
const fieldLabel: CSSProperties = { fontSize: 12, color: "var(--muted)", fontWeight: 600, marginBottom: 6 };
const inputBox: CSSProperties = { border: "1px solid var(--border)", borderRadius: 3, padding: "11px 13px", fontSize: 14, background: "var(--input)" };
const accentPill: CSSProperties = { borderRadius: 999, padding: "9px 18px", fontSize: 13, fontWeight: 600, color: "#fff", background: "var(--accent)", boxShadow: "0 2px 6px rgba(22,24,29,.3)", cursor: "pointer" };
const savePill: CSSProperties = { borderRadius: 999, padding: "10px 20px", fontSize: 13, fontWeight: 600, color: "#fff", background: "var(--accent)", cursor: "pointer" };
const connectPill: CSSProperties = { border: "1px solid var(--border)", borderRadius: 999, padding: "6px 14px", fontSize: 13, fontWeight: 600, cursor: "pointer" };
const greenPill: CSSProperties = { color: "#047857", background: "#ECFDF3", borderRadius: 3, padding: "3px 11px", fontSize: 12, fontWeight: 600 };
const tableHead: CSSProperties = { display: "flex", fontSize: 12, color: "var(--faint)", padding: "12px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", fontWeight: 600, background: "var(--th)" };

// Prototype toggle: 42×24 pill, on = accent + knob right, off = fill + knob left.
function Toggle({ on }: { on: boolean }) {
  return (
    <div style={{ width: 42, height: 24, borderRadius: 999, background: on ? "var(--accent)" : "var(--fill)", padding: 2, display: "flex", justifyContent: on ? "flex-end" : "flex-start" }}>
      <div style={{ width: 20, height: 20, borderRadius: "50%", background: "#fff" }} />
    </div>
  );
}

function Field({ label, value, flex }: { label: string; value: string; flex?: number }) {
  return (
    <div style={flex ? { flex } : undefined}>
      <div style={fieldLabel}>{label}</div>
      <div style={inputBox}>{value}</div>
    </div>
  );
}

export function Settings({ tab }: { tab?: string }) {
  const { t } = useTranslation();
  const [active, setActive] = useState(tab ?? PROTO_DEFAULT_TAB);

  const navItem = (key: string, label: string): ReactNode => {
    const on = active === key;
    return (
      <div
        key={key}
        onClick={() => setActive(key)}
        style={{
          padding: "8px 11px",
          borderRadius: 3,
          cursor: "pointer",
          fontWeight: on ? 600 : 400,
          color: on ? "var(--accent-fg)" : "var(--muted)",
          background: on ? "var(--accent-soft)" : "transparent",
        }}
      >
        {label}
      </div>
    );
  };

  return (
    <div style={{ display: "flex", gap: 20, alignItems: "flex-start" }}>
      {/* left settings sub-nav */}
      <div style={{ width: 210, flex: "none", background: "var(--surface)", border: "1px solid var(--border)", borderRadius: 2, padding: 10, display: "flex", flexDirection: "column", gap: 2, fontSize: 14, boxShadow: "0 1px 2px rgba(16,24,40,.04)" }}>
        {SETTINGS_TABS.map((s) => navItem(s.key, t(s.labelKey)))}
      </div>

      {/* active tab content panel */}
      <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 18, minWidth: 0 }}>

        {active === "general" && (
          <>
            <div style={sectionTitle}>{t("settings:general")}</div>
            <div style={{ ...card, padding: 18, display: "flex", flexDirection: "column", gap: 16, maxWidth: 620 }}>
              <Field label={t("settings:shopName")} value={GENERAL_DEFAULTS.shopName} />
              <div style={{ display: "flex", gap: 14 }}>
                <Field label={t("settings:contactEmail")} value={GENERAL_DEFAULTS.contactEmail} flex={1} />
                <Field label={t("settings:phone")} value={GENERAL_DEFAULTS.phone} flex={1} />
              </div>
              <Field label={t("settings:address")} value={GENERAL_DEFAULTS.address} />
              <div style={{ display: "flex", gap: 14 }}>
                <Field label={t("settings:currency")} value={`${GENERAL_DEFAULTS.currency} ▾`} flex={1} />
                <Field label={t("settings:timezone")} value={`${GENERAL_DEFAULTS.timezone} ▾`} flex={1} />
              </div>
              <div style={{ display: "flex", justifyContent: "flex-end" }}>
                <div style={savePill}>{t("settings:save")}</div>
              </div>
            </div>
          </>
        )}

        {active === "team" && (
          <>
            <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
              <div style={sectionTitle}>{t("settings:team")}</div>
              <div style={{ flex: 1 }} />
              <div style={accentPill}>+ {t("settings:inviteMember")}</div>
            </div>
            <div data-table style={card}>
              <div style={tableHead}>
                <div style={{ flex: 1.2 }}>{t("settings:colMember")}</div>
                <div style={{ flex: 1.3 }}>{t("settings:colEmail")}</div>
                <div style={{ width: 130 }}>{t("settings:colRole")}</div>
                <div style={{ width: 90 }}>{t("settings:colStatus")}</div>
              </div>
              {TEAM.map((m) => (
                <div key={m.email} style={{ display: "flex", fontSize: 14, padding: "12px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", alignItems: "center" }}>
                  <div style={{ flex: 1.2, display: "flex", alignItems: "center", gap: 9 }}>
                    <span style={{ width: 30, height: 30, borderRadius: "50%", background: "var(--accent-soft)", color: "var(--accent-fg)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, flex: "none" }}>{m.initial}</span>
                    {m.name}
                  </div>
                  <div style={{ flex: 1.3, color: "var(--muted)" }}>{m.email}</div>
                  <div style={{ width: 130 }}><span style={{ fontSize: 12, color: "var(--muted)", background: "var(--fill)", borderRadius: 3, padding: "3px 10px", fontWeight: 600 }}>{m.role}</span></div>
                  <div style={{ width: 90 }}><StatusPill status={m.status} label={t(`settings:memberStatus_${m.status}`)} /></div>
                </div>
              ))}
            </div>
            <div style={{ fontSize: 15, fontWeight: 700 }}>{t("settings:rolePermissions")}</div>
            <div style={{ display: "flex", gap: 16 }}>
              {(["admin", "staff", "instructor"] as const).map((r) => (
                <div key={r} style={{ ...card, flex: 1, padding: 16 }}>
                  <div style={{ fontSize: 14, fontWeight: 700, marginBottom: 6 }}>{t(`settings:role_${r}`)}</div>
                  <div style={{ fontSize: 13, color: "var(--muted)", lineHeight: 1.5 }}>{t(`settings:roleDesc_${r}`)}</div>
                </div>
              ))}
            </div>
          </>
        )}

        {active === "payments" && (
          <>
            <div style={sectionTitle}>{t("settings:payments")}</div>
            <div style={{ ...card, overflow: "hidden", maxWidth: 720 }}>
              {/* Barion */}
              <div style={{ display: "flex", alignItems: "center", gap: 14, padding: "14px 18px", borderBottom: "1px solid var(--border-soft)" }}>
                <div style={{ width: 40, height: 40, borderRadius: 3, background: "var(--fill)", flex: "none" }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>{t("settings:payBarion")}</div>
                  <div style={{ fontSize: 12, color: "var(--muted)" }}>{t("settings:payBarionDesc")}</div>
                </div>
                <span style={greenPill}>{t("settings:on")}</span>
                <Toggle on />
              </div>
              {/* Bank transfer */}
              <div style={{ display: "flex", alignItems: "center", gap: 14, padding: "14px 18px", borderBottom: "1px solid var(--border-soft)" }}>
                <div style={{ width: 40, height: 40, borderRadius: 3, background: "var(--fill)", flex: "none" }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>{t("settings:payTransfer")}</div>
                  <div style={{ fontSize: 12, color: "var(--muted)" }}>{t("settings:payTransferDesc")}</div>
                </div>
                <span style={greenPill}>{t("settings:on")}</span>
                <Toggle on />
              </div>
              {/* COD */}
              <div style={{ display: "flex", alignItems: "center", gap: 14, padding: "14px 18px", borderBottom: "1px solid var(--border-soft)" }}>
                <div style={{ width: 40, height: 40, borderRadius: 3, background: "var(--fill)", flex: "none" }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>{t("settings:payCod")}</div>
                  <div style={{ fontSize: 12, color: "var(--muted)" }}>{t("settings:payCodDesc")}</div>
                </div>
                <span style={greenPill}>{t("settings:on")}</span>
                <Toggle on />
              </div>
              {/* PayPal */}
              <div style={{ display: "flex", alignItems: "center", gap: 14, padding: "14px 18px" }}>
                <div style={{ width: 40, height: 40, borderRadius: 3, background: "var(--fill)", flex: "none" }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>{t("settings:payPaypal")}</div>
                  <div style={{ fontSize: 12, color: "var(--muted)" }}>{t("settings:payPaypalDesc")}</div>
                </div>
                <div style={connectPill}>{t("settings:connect")}</div>
                <Toggle on={false} />
              </div>
            </div>
          </>
        )}

        {active === "shipping" && (
          <>
            <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
              <div style={sectionTitle}>{t("settings:shippingZones")}</div>
              <div style={{ flex: 1 }} />
              <div style={{ ...accentPill, boxShadow: undefined }}>+ {t("settings:addZone")}</div>
            </div>
            <div data-table style={{ ...card, maxWidth: 820 }}>
              <div style={tableHead}>
                <div style={{ width: 160 }}>{t("settings:colZone")}</div>
                <div style={{ flex: 1 }}>{t("settings:colMethods")}</div>
                <div style={{ width: 120, textAlign: "right" }}>{t("settings:colRate")}</div>
              </div>
              <div style={{ display: "flex", fontSize: 14, padding: "13px 18px", gap: 16, borderBottom: "1px solid var(--border-soft)", alignItems: "center" }}>
                <div style={{ width: 160, fontWeight: 600 }}>{t("settings:zoneHu")}</div>
                <div style={{ flex: 1, color: "var(--muted)" }}>Foxpost · Packeta · MPL · GLS futár</div>
                <div style={{ width: 120, textAlign: "right" }}>990–1 690 Ft</div>
              </div>
              <div style={{ display: "flex", fontSize: 14, padding: "13px 18px", gap: 16, alignItems: "center" }}>
                <div style={{ width: 160, fontWeight: 600 }}>{t("settings:zoneEu")}</div>
                <div style={{ flex: 1, color: "var(--muted)" }}>GLS futár</div>
                <div style={{ width: 120, textAlign: "right" }}>4 500 Ft</div>
              </div>
            </div>
            <div style={{ fontSize: 13, color: "var(--muted)" }}>{t("settings:freeShipNote")}</div>
          </>
        )}

        {active === "taxes" && (
          <>
            <div style={sectionTitle}>{t("settings:taxes")}</div>
            <div style={{ ...card, padding: 18, display: "flex", flexDirection: "column", gap: 16, maxWidth: 620 }}>
              <div style={{ display: "flex", gap: 14 }}>
                <Field label={t("settings:baseVat")} value="27% ▾" flex={1} />
                <Field label={t("settings:priceDisplay")} value={`${t("settings:priceGross")} ▾`} flex={1} />
              </div>
              <div style={{ display: "flex", gap: 14 }}>
                <Field label={t("settings:invoicer")} value="Számlázz.hu ▾" flex={1} />
                <Field label={t("settings:invoicePrefix")} value="NK-2026-" flex={1} />
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 12, border: "1px solid var(--border-soft)", borderRadius: 3, padding: "12px 14px", background: "var(--input)" }}>
                <span style={greenPill}>{t("settings:connected")}</span>
                <div style={{ fontSize: 13, color: "var(--muted)" }}>{t("settings:navNote")}</div>
              </div>
              <div style={{ display: "flex", justifyContent: "flex-end" }}>
                <div style={savePill}>{t("settings:save")}</div>
              </div>
            </div>
          </>
        )}

        {active === "languages" && (
          <>
            <div style={sectionTitle}>{t("settings:languages")}</div>
            <div style={{ ...card, overflow: "hidden", maxWidth: 620 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 14, padding: "14px 18px", borderBottom: "1px solid var(--border-soft)" }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>{t("settings:langHu")}</div>
                  <div style={{ fontSize: 12, color: "var(--muted)" }}>{t("settings:langHuDesc")}</div>
                </div>
                <span style={{ color: "var(--muted)", background: "var(--fill)", borderRadius: 3, padding: "3px 11px", fontSize: 12, fontWeight: 600 }}>{t("settings:langDefault")}</span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: 14, padding: "14px 18px" }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>{t("settings:langEn")}</div>
                  <div style={{ fontSize: 12, color: "var(--muted)" }}>{t("settings:langEnDesc")}</div>
                </div>
                <Toggle on />
              </div>
            </div>
            <div style={{ fontSize: 13, color: "var(--muted)" }}>{t("settings:langNote")}</div>
          </>
        )}

        {active === "notifications" && (
          <>
            <div style={sectionTitle}>{t("settings:notifications")}</div>
            <div style={{ ...card, overflow: "hidden", maxWidth: 620 }}>
              {NOTIF_PREFS.map((n, i) => (
                <div key={n.label} style={{ display: "flex", alignItems: "center", gap: 14, padding: "14px 18px", borderBottom: i < NOTIF_PREFS.length - 1 ? "1px solid var(--border-soft)" : undefined }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: 14, fontWeight: 600 }}>{n.label}</div>
                    <div style={{ fontSize: 12, color: "var(--muted)" }}>{n.desc}</div>
                  </div>
                  <Toggle on={n.on} />
                </div>
              ))}
            </div>
          </>
        )}

        {active === "integrations" && (
          <>
            <div style={sectionTitle}>{t("settings:integrations")}</div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(2,1fr)", gap: 16, maxWidth: 820 }}>
              <div style={{ ...card, padding: 16, display: "flex", alignItems: "center", gap: 12 }}>
                <div style={{ width: 40, height: 40, borderRadius: 3, background: "var(--fill)", flex: "none" }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>Számlázz.hu</div>
                  <div style={{ fontSize: 12, color: "var(--muted)" }}>{t("settings:intInvoicing")}</div>
                </div>
                <span style={{ ...greenPill, padding: "3px 10px" }}>{t("settings:on")}</span>
              </div>
              <div style={{ ...card, padding: 16, display: "flex", alignItems: "center", gap: 12 }}>
                <div style={{ width: 40, height: 40, borderRadius: 3, background: "var(--fill)", flex: "none" }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>Google Analytics</div>
                  <div style={{ fontSize: 12, color: "var(--muted)" }}>{t("settings:intAnalytics")}</div>
                </div>
                <span style={{ ...greenPill, padding: "3px 10px" }}>{t("settings:on")}</span>
              </div>
              <div style={{ ...card, padding: 16, display: "flex", alignItems: "center", gap: 12 }}>
                <div style={{ width: 40, height: 40, borderRadius: 3, background: "var(--fill)", flex: "none" }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>Mailchimp</div>
                  <div style={{ fontSize: 12, color: "var(--muted)" }}>{t("settings:intNewsletter")}</div>
                </div>
                <div style={connectPill}>{t("settings:connect")}</div>
              </div>
              <div style={{ ...card, padding: 16, display: "flex", alignItems: "center", gap: 12 }}>
                <div style={{ width: 40, height: 40, borderRadius: 3, background: "var(--fill)", flex: "none" }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 14, fontWeight: 600 }}>Meta Pixel</div>
                  <div style={{ fontSize: 12, color: "var(--muted)" }}>{t("settings:intAdsTracking")}</div>
                </div>
                <div style={connectPill}>{t("settings:connect")}</div>
              </div>
            </div>
          </>
        )}

      </div>
    </div>
  );
}
