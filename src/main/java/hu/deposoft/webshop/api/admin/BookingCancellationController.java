package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.order.BookingCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Line-level booking cancel for the admin SPA (2b-2). ADMIN-gated by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/order-items")
@RequiredArgsConstructor
public class BookingCancellationController {

    private final BookingCancellationService bookings;

    public record BookingCancelResult(long orderItemId, long refundedHuf) {
    }

    @PostMapping("/{itemId}/cancel")
    public BookingCancelResult cancel(@PathVariable Long itemId) {
        long refunded = bookings.cancelBooking(itemId);
        return new BookingCancelResult(itemId, refunded);
    }
}
