package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.order.BookingRescheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Line-level booking reschedule for the admin SPA (2b-3). ADMIN-gated by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/order-items")
@RequiredArgsConstructor
public class BookingRescheduleController {

    private final BookingRescheduleService reschedules;

    public record RescheduleRequest(@NotNull Long targetSessionId) {
    }

    public record RescheduleResult(long orderItemId, long sessionId) {
    }

    @PostMapping("/{itemId}/reschedule")
    public RescheduleResult reschedule(@PathVariable Long itemId, @RequestBody @Valid RescheduleRequest req) {
        reschedules.reschedule(itemId, req.targetSessionId());
        return new RescheduleResult(itemId, req.targetSessionId());
    }
}
