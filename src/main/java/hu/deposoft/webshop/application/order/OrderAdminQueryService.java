package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** Read-only order views for the admin (slice 1): list with filters + detail. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderAdminQueryService {

    private final OrderRepository orders;

    /** An order id did not resolve. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    public record OrderSummary(Long id, String orderNumber, OrderStatus status, String customerName,
                               String email, long totalGrossHuf, OffsetDateTime createdAt) {
    }

    public record PageResult(List<OrderSummary> items, long total) {
    }

    public record LineView(String productName, String variantLabel, String sku, int quantity, long lineGrossHuf) {
    }

    public record OrderDetail(Long id, String orderNumber, OrderStatus status, String customerName,
                              String email, String phone, String postcode, String city, String addressLine,
                              String shipMethodName, long shipGrossHuf, long itemsGrossHuf, long totalGrossHuf,
                              OffsetDateTime createdAt, List<LineView> lines) {
    }

    public PageResult list(OrderStatus status, OffsetDateTime from, OffsetDateTime to, String q,
                           int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        String term = (q == null || q.isBlank()) ? null : q.trim();
        String statusStr = (status == null) ? null : status.name();
        Page<Order> result = orders.search(statusStr, from, to, term, PageRequest.of(safePage, safeSize));
        List<OrderSummary> items = result.getContent().stream()
                .map(o -> new OrderSummary(o.getId(), o.orderNumber(), o.getStatus(), o.getCustomerName(),
                        o.getEmail(), o.getTotalGrossHuf(), o.getCreatedAt()))
                .toList();
        return new PageResult(items, result.getTotalElements());
    }

    public OrderDetail detail(Long id) {
        Order o = orders.findWithItemsById(id)
                .orElseThrow(() -> new NotFoundException("No order " + id));
        List<LineView> lines = o.getItems().stream()
                .map(this::toLine)
                .toList();
        return new OrderDetail(o.getId(), o.orderNumber(), o.getStatus(), o.getCustomerName(), o.getEmail(),
                o.getPhone(), o.getPostcode(), o.getCity(), o.getAddressLine(), o.getShipMethodName(),
                o.getShipGrossHuf(), o.getItemsGrossHuf(), o.getTotalGrossHuf(), o.getCreatedAt(), lines);
    }

    private LineView toLine(OrderItem i) {
        return new LineView(i.getProductName(), i.getVariantLabel(), i.getSku(), i.getQuantity(), i.getLineGrossHuf());
    }
}
