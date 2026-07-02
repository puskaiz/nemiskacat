// Static example data mirroring the prototype's Settings screen
// (Admin Mid-fi HU.dc.html, sec.settings block, lines 615-718). No backend
// wiring in this pass; each tab renders the prototype's content verbatim.

export interface SettingsTab {
  key: string;
  labelKey: string;
}

// Order + keys match the prototype's left settings sub-nav.
export const SETTINGS_TABS: SettingsTab[] = [
  { key: "general", labelKey: "settings:general" },
  { key: "team", labelKey: "settings:team" },
  { key: "payments", labelKey: "settings:payments" },
  { key: "shipping", labelKey: "settings:shippingZones" },
  { key: "taxes", labelKey: "settings:taxes" },
  { key: "languages", labelKey: "settings:languages" },
  { key: "notifications", labelKey: "settings:notifications" },
  { key: "integrations", labelKey: "settings:integrations" },
];

export const GENERAL_DEFAULTS = {
  shopName: "Némiskacat",
  contactEmail: "hello@nemiskacat.hu",
  phone: "+36 1 234 5678",
  address: "1052 Budapest, Váci utca 12.",
  currency: "HUF — Ft",
  timezone: "Europe/Budapest",
};

// Team & roles tab (line 650).
export interface TeamMember {
  initial: string;
  name: string;
  email: string;
  role: string;
  status: string;
}

export const TEAM: TeamMember[] = [
  { initial: "AN", name: "Anna Nagy", email: "anna@nemiskacat.hu", role: "Admin", status: "active" },
  { initial: "BK", name: "Bence Kovács", email: "bence@nemiskacat.hu", role: "Munkatárs", status: "active" },
  { initial: "CS", name: "Csilla Szabó", email: "csilla@nemiskacat.hu", role: "Munkatárs", status: "active" },
  { initial: "DT", name: "Dóra Tóth", email: "dora@nemiskacat.hu", role: "Oktató", status: "active" },
  { initial: "EH", name: "Eszter Horváth", email: "eszter@nemiskacat.hu", role: "Oktató", status: "pending" },
];

// Notifications tab (line 702).
export interface NotifPref {
  label: string;
  desc: string;
  on: boolean;
}

export const NOTIF_PREFS: NotifPref[] = [
  { label: "Új rendelés", desc: "E-mail minden új rendelésnél", on: true },
  { label: "Alacsony készlet", desc: "Figyelmeztetés, ha egy termék készlete fogytán", on: true },
  { label: "Új workshop-foglalás", desc: "E-mail minden új foglalásnál", on: true },
  { label: "Heti összegző", desc: "Hétfőnként riport az előző hét teljesítményéről", on: false },
];
