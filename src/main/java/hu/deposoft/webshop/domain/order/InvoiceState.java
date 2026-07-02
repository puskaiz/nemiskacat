package hu.deposoft.webshop.domain.order;

/** Outcome of invoicing a group of order lines for one source. */
public enum InvoiceState {
    /** Billingo invoice issued by us. */
    ISSUED,
    /** Order handed over to Kulcs-Soft (they issue). */
    PUSHED,
    FAILED;

    public boolean isSuccessful() {
        return this == ISSUED || this == PUSHED;
    }
}
