package hu.deposoft.webshop.domain.catalog;

/** How a line is fulfilled. SHIP = physical delivery (counts for shipping fee);
 *  EVENT = a workshop seat (no shipping). */
public enum FulfilmentType {
    SHIP,
    EVENT
}
