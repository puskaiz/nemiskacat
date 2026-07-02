package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.order.OrderAdminQueryService;
import hu.deposoft.webshop.application.order.OrderAdminQueryService.OrderDetail;
import hu.deposoft.webshop.application.order.OrderAdminQueryService.OrderSummary;
import hu.deposoft.webshop.application.order.OrderAdminQueryService.PageResult;
import hu.deposoft.webshop.application.order.OrderAdminService;
import hu.deposoft.webshop.application.order.RefundService;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/** Read-only order views for the admin SPA (slice 1). ADMIN-gated by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderAdminQueryService query;
    private final OrderAdminService orderAdmin;
    private final RefundService refundService;

    @GetMapping
    public List<OrderSummary> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletResponse response) {
        PageResult result = query.list(status, from, to, q, page, size);
        response.setHeader("X-Total-Count", String.valueOf(result.total()));
        return result.items();
    }

    @GetMapping("/{id}")
    public OrderDetail detail(@PathVariable Long id) {
        return query.detail(id);
    }

    public record TransitionRequest(@NotNull OrderStatus status) {
    }

    @PostMapping("/{id}/transition")
    public OrderDetail transition(@PathVariable Long id, @RequestBody @Valid TransitionRequest req) {
        orderAdmin.transition(id, req.status());
        return query.detail(id);
    }

    @PostMapping("/{id}/refund")
    public OrderDetail refund(@PathVariable Long id) {
        refundService.refund(id);
        return query.detail(id);
    }
}
