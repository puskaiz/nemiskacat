// Static example data mirroring the prototype's Instructors list
// (Admin Mid-fi HU.dc.html, wInstr block, line 991). The status maps to a
// StatusPill tone via toneFor(): "active" → green, "leave" → gray, matching
// the prototype's pill(status==='Aktív'?'green':'gray').
export interface InstructorRow {
  name: string;
  email: string;
  specialty: string;
  count: number;
  status: "active" | "leave";
}

export const INSTRUCTORS: InstructorRow[] = [
  { name: "Anna Kovács", email: "anna@nemiskacat.hu", specialty: "Pottery · Ceramics", count: 6, status: "active" },
  { name: "Béla Nagy", email: "bela@nemiskacat.hu", specialty: "Macramé · Textile", count: 3, status: "active" },
  { name: "Júlia Horváth", email: "julia@nemiskacat.hu", specialty: "Watercolor", count: 4, status: "active" },
  { name: "Eszter Tóth", email: "eszter@nemiskacat.hu", specialty: "Pottery", count: 2, status: "leave" },
];
