package hu.deposoft.webshop.domain.order;

/** Whether an invoice row records the original invoice or its storno (credit note). */
public enum InvoiceType {
    INVOICE,
    CREDIT_NOTE
}
