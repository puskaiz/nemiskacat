package hu.deposoft.webshop.domain.catalog;

/** Which system invoices a line. Physical goods are invoiced in Kulcs-Soft (we push
 *  the order); workshops are invoiced by our app via Billingo. */
public enum InvoiceSource {
    KULCS_SOFT,
    BILLINGO
}
