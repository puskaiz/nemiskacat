package hu.deposoft.webshop.application.invoicing;

import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;

import java.util.List;

/** Port: hands a group of order lines over to an external system that issues the
 *  invoice itself (Kulcs-Soft for physical goods — we only push). */
public interface OrderSink {

    void push(Order order, List<OrderItem> lines);

    /** Hands a return/credit for these lines to Kulcs-Soft (they issue the credit note). */
    void pushCreditNote(Order order, List<OrderItem> lines);
}
