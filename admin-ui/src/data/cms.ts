// Static example data mirroring the design's Pages (CMS) screen.
export interface CmsPageRow {
  title: string;
  url: string;
  updated: string;
  status: string;
}

export const CMS_PAGES: CmsPageRow[] = [
  { title: "Rólunk", url: "/rolunk", updated: "2026-06-10", status: "active" },
  { title: "Kapcsolat", url: "/kapcsolat", updated: "2026-05-22", status: "active" },
  { title: "Szállítás és fizetés", url: "/szallitas-fizetes", updated: "2026-06-01", status: "active" },
  { title: "ÁSZF", url: "/aszf", updated: "2026-04-18", status: "active" },
  { title: "Adatkezelési tájékoztató", url: "/adatkezeles", updated: "2026-04-18", status: "active" },
  { title: "Nyári workshop kampány", url: "/nyari-kampany", updated: "2026-06-14", status: "pending" },
];
