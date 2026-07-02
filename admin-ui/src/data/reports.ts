// Static example data mirroring the prototype's Reports screen
// (Admin Mid-fi HU.dc.html, sec.reports block, lines 538-572). Swapped for a
// real reporting backend in a later pass; shape kept faithful to the prototype.

// 4 KPI cards. Values + the green delta are taken verbatim from the prototype.
export const REPORT_KPIS = [
  { labelKey: "reports:revenue", value: "1.84M Ft", delta: "▲ 12%" },
  { labelKey: "reports:orders", value: "312", delta: "▲ 6%" },
  { labelKey: "reports:aov", value: "5 900 Ft", delta: "▲ 4%" },
  { labelKey: "reports:workshopRevenue", value: "486K Ft", delta: "▲ 22%" },
];

// Bar heights for the "Bevétel az időben" chart (line 907) — 14 bars, verbatim.
export const CHART_BARS = ["38%", "52%", "45%", "61%", "58%", "72%", "66%", "80%", "74%", "88%", "69%", "92%", "83%", "97%"];

// Top products list (lines 561-564).
export const TOP_PRODUCTS = [
  { name: "Kerámia bögre", sold: 84 },
  { name: "Lenvászon kendő", sold: 61 },
  { name: "Illatgyertya", sold: 57 },
  { name: "Kerámia tál", sold: 43 },
];

// Sales by channel bars (lines 566-568).
export const SALES_BY_CHANNEL = [
  { labelKey: "reports:channelWeb", pct: 64 },
  { labelKey: "reports:channelPos", pct: 24 },
  { labelKey: "reports:channelWorkshop", pct: 12 },
];
