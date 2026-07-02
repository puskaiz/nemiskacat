package hu.deposoft.webshop.application.invoicing;

/** Published after an order transitions to PAID (commit) so invoicing can run. */
public record OrderPaidEvent(Long orderId) {
}
