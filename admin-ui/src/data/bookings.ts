// Static example data mirroring the prototype Bookings screen
// (Admin Mid-fi HU.dc.html, sec.bookings block, lines 382-405). The prototype
// renders 8 placeholder rows in the "Upcoming" tab.
export interface BookingRow {
  ref: string;
  initial: string;
  name: string;
  ws: string;
  seats: number;
  paid: string;
  status: string;
}

export const BOOKINGS: BookingRow[] = [
  { ref: "WB-2031", initial: "T", name: "Tóth Eszter", ws: "Kerámia este · jún. 21", seats: 2, paid: "16 200 Ft", status: "paid" },
  { ref: "WB-2030", initial: "N", name: "Nagy Péter", ws: "Bútorfelújítás · jún. 24", seats: 1, paid: "9 000 Ft", status: "paid" },
  { ref: "WB-2029", initial: "S", name: "Szabó Júlia", ws: "Patinázás haladó · jún. 28", seats: 1, paid: "12 000 Ft", status: "upcoming" },
  { ref: "WB-2028", initial: "H", name: "Horváth Gábor", ws: "Kerámia este · júl. 5", seats: 3, paid: "27 000 Ft", status: "paid" },
  { ref: "WB-2027", initial: "V", name: "Varga Réka", ws: "Viaszolás workshop · júl. 12", seats: 1, paid: "0 Ft", status: "waitlist" },
  { ref: "WB-2026", initial: "K", name: "Kovács Anna", ws: "Kerámia este · júl. 19", seats: 2, paid: "18 000 Ft", status: "paid" },
  { ref: "WB-2025", initial: "F", name: "Fekete Dóra", ws: "Bútorfelújítás · júl. 22", seats: 1, paid: "9 000 Ft", status: "upcoming" },
  { ref: "WB-2024", initial: "B", name: "Balogh Márk", ws: "Patinázás haladó · júl. 26", seats: 2, paid: "24 000 Ft", status: "paid" },
];
