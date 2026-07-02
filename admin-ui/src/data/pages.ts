// Static example data mirroring the prototype's Pages (CMS) screen
// (Admin Mid-fi HU.dc.html, sec.cms block, line 586). Real CMS content arrives
// from content/blog Markdown + page entities in a later pass.
export interface CmsPageRow {
  title: string;
  slug: string;
  langs: string;
  updated: string;
  status: string;
}

export const CMS_PAGES: CmsPageRow[] = [
  { title: "Rólunk", slug: "/rolunk", langs: "HU · EN", updated: "2026-06-10", status: "active" },
  { title: "Kapcsolat", slug: "/kapcsolat", langs: "HU", updated: "2026-05-22", status: "active" },
  { title: "Szállítás és fizetés", slug: "/szallitas-fizetes", langs: "HU", updated: "2026-06-01", status: "active" },
  { title: "ÁSZF", slug: "/aszf", langs: "HU", updated: "2026-04-18", status: "active" },
  { title: "Adatkezelési tájékoztató", slug: "/adatkezeles", langs: "HU", updated: "2026-04-18", status: "active" },
  { title: "Gyakori kérdések", slug: "/gyik", langs: "HU", updated: "2026-05-30", status: "active" },
  { title: "Nyári workshop kampány", slug: "/nyari-kampany", langs: "HU", updated: "2026-06-14", status: "pending" },
];
