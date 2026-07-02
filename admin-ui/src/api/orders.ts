import { apiFetch, API_BASE } from "./http";
import type { OrderStatus } from "../types";

export const STATUS_COLORS: Record<OrderStatus, string> = {
  NEW: "blue",
  PAID: "green",
  PACKING: "gold",
  SHIPPED: "geekblue",
  COMPLETED: "default",
  CANCELLED: "red",
  REFUNDED: "purple",
  PROCESSING: "gold",
  ON_HOLD: "orange",
  FAILED: "red",
  AWAITING_SHIPMENT: "geekblue",
};

/** Hungarian display labels for the order statuses (the API returns the enum names). */
export const STATUS_LABELS: Record<OrderStatus, string> = {
  NEW: "Fizetésre vár",
  PAID: "Fizetve",
  PACKING: "Feldolgozás alatt",
  SHIPPED: "Futárnak átadva",
  COMPLETED: "Teljesítve",
  CANCELLED: "Visszamondva",
  REFUNDED: "Visszatérítve",
  PROCESSING: "Feldolgozás alatt (Woo)",
  ON_HOLD: "Fizetésre vár (Woo)",
  FAILED: "Sikertelen (Woo)",
  AWAITING_SHIPMENT: "Szállításra vár (Woo)",
};

/** The single allowed next state per status for the daily fulfilment flow (slice 2a). */
export const NEXT: Partial<
  Record<OrderStatus, { target: OrderStatus; label: string; confirm?: boolean }>
> = {
  NEW: { target: "CANCELLED", label: "Lemondás", confirm: true },
  PAID: { target: "PACKING", label: "Csomagolásba" },
  PACKING: { target: "SHIPPED", label: "Feladva" },
  SHIPPED: { target: "COMPLETED", label: "Teljesítve" },
};

/** Admin-settable target statuses (PAID is set by payment, not the admin). */
export const TARGET_STATUSES: OrderStatus[] = ["PACKING", "SHIPPED", "COMPLETED", "CANCELLED"];

export async function transitionOrder(id: number, status: OrderStatus): Promise<Response> {
  return apiFetch(`${API_BASE}/orders/${id}/transition`, {
    method: "POST",
    body: JSON.stringify({ status }),
  });
}

export async function refundOrder(id: number): Promise<Response> {
  return apiFetch(`${API_BASE}/orders/${id}/refund`, { method: "POST" });
}

export async function cancelBooking(itemId: number): Promise<Response> {
  return apiFetch(`${API_BASE}/order-items/${itemId}/cancel`, { method: "POST" });
}

export async function rescheduleBooking(
  itemId: number,
  targetSessionId: number,
): Promise<Response> {
  return apiFetch(`${API_BASE}/order-items/${itemId}/reschedule`, {
    method: "POST",
    body: JSON.stringify({ targetSessionId }),
  });
}
