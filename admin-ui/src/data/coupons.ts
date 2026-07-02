// Static example data mirroring the design's Coupons screen
// (Admin Mid-fi HU.dc.html, sec.coupons block). Status maps to the squared
// pill: "active" (green "Aktív") / "neutral" (gray "Lejárt"/"Vázlat").
export interface CouponRow {
  code: string;
  type: string;
  value: string;
  usage: string;
  expires: string;
  status: "active" | "neutral";
  statusLabel: string;
}

export const COUPONS: CouponRow[] = [
  { code: "NYAR10", type: "Százalék", value: "10%", usage: "42 / 200", expires: "2026-08-31", status: "active", statusLabel: "Aktív" },
  { code: "UDVOZLO", type: "Fix összeg", value: "1 500 Ft", usage: "118 / ∞", expires: "—", status: "active", statusLabel: "Aktív" },
  { code: "INGYENSZALLITAS", type: "Ingyenes szállítás", value: "—", usage: "63 / 500", expires: "2026-07-15", status: "active", statusLabel: "Aktív" },
  { code: "WORKSHOP20", type: "Százalék", value: "20%", usage: "9 / 50", expires: "2026-06-30", status: "active", statusLabel: "Aktív" },
  { code: "BLACKFRIDAY", type: "Százalék", value: "25%", usage: "0 / 1000", expires: "2026-11-29", status: "neutral", statusLabel: "Vázlat" },
  { code: "TAVASZ15", type: "Százalék", value: "15%", usage: "204 / 204", expires: "2026-05-31", status: "neutral", statusLabel: "Lejárt" },
];
