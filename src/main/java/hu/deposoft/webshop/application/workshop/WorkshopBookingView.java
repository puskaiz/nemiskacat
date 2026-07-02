package hu.deposoft.webshop.application.workshop;

import hu.deposoft.webshop.domain.checkout.OrderStatus;

import java.time.OffsetDateTime;

/**
 * One workshop booking line for the admin (T24): which session, who booked it,
 * how to reach them, and how many seats. Sourced from non-cancelled order lines
 * whose variant is one of the workshop's session seats.
 */
public record WorkshopBookingView(
        Long sessionId,
        OffsetDateTime sessionStartAt,
        String sessionSku,
        String orderNumber,
        OrderStatus orderStatus,
        String customerName,
        String email,
        String phone,
        int seats,
        long orderItemId,
        int cancelledSeats,
        long lineGrossHuf,
        long unitGrossHuf) {
}
