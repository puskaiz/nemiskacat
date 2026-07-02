export type Tone = "green" | "amber" | "blue" | "gray";

const MAP: Record<string, Tone> = {
  paid: "green", active: "green", completed: "green", attended: "green",
  pending: "amber", packing: "amber", waitlist: "amber", upcoming: "amber",
  shipped: "blue", new: "blue",
  refunded: "gray", cancelled: "gray", neutral: "gray",
};
export function toneFor(status: string): Tone {
  return MAP[status.toLowerCase()] ?? "gray";
}
