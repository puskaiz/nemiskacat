// Static example data mirroring the prototype Fulfilment / Shipping screen
// (Admin Mid-fi HU.dc.html, sec.shipping block, lines 509-535). The prototype
// renders 7 placeholder rows in the "To pack" tab.
export interface ShipmentRow {
  id: string;
  initial: string;
  name: string;
  items: number;
  city: string;
  method: string;
}

export const SHIPMENTS: ShipmentRow[] = [
  { id: "1042", initial: "K", name: "Kovács Anna", items: 3, city: "Budapest, 1052", method: "Foxpost" },
  { id: "1041", initial: "T", name: "Tóth Eszter", items: 1, city: "Debrecen, 4025", method: "GLS futár" },
  { id: "1039", initial: "N", name: "Nagy Péter", items: 5, city: "Szeged, 6720", method: "Packeta" },
  { id: "1037", initial: "S", name: "Szabó Júlia", items: 2, city: "Győr, 9021", method: "MPL" },
  { id: "1035", initial: "V", name: "Varga Réka", items: 4, city: "Pécs, 7621", method: "Foxpost" },
  { id: "1034", initial: "H", name: "Horváth Gábor", items: 1, city: "Miskolc, 3525", method: "GLS futár" },
  { id: "1032", initial: "F", name: "Fekete Dóra", items: 2, city: "Budapest, 1075", method: "Packeta" },
];
