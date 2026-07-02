package hu.deposoft.webshop.integrations.invoicing;

import hu.deposoft.webshop.application.invoicing.OrderSink;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hands physical order lines to Kulcs-Soft, which issues their invoice. The real
 * push (export/API) is [EMBER]-blocked (Kulcs access / D9); for now it logs and
 * succeeds so the order is marked "handed over". Replace with the real adapter
 * when Kulcs access exists.
 */
@Component
@Slf4j
public class KulcsSoftOrderSink implements OrderSink {

    @Override
    public void push(Order order, List<OrderItem> lines) {
        log.info("Kulcs-Soft hand-over (stub) for order {}: {} physical line(s) — wire the real push when Kulcs access exists",
                order.orderNumber(), lines.size());
    }

    @Override
    public void pushCreditNote(Order order, List<OrderItem> lines) {
        log.info("Kulcs-Soft credit-note hand-over (stub) for order {}: {} physical line(s) — wire the real return push when Kulcs access exists",
                order.orderNumber(), lines.size());
    }
}
