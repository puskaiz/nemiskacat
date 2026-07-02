package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.domain.catalog.StockAvailability;
import hu.deposoft.webshop.domain.catalog.StockStatus;
import hu.deposoft.webshop.domain.catalog.StockStatusCalculator;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.ReservationRepository;
import hu.deposoft.webshop.domain.workshop.WorkshopSession;
import hu.deposoft.webshop.domain.workshop.WorkshopSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * The availability ledger (TERV §3.7), in one place:
 * available = last synced stock − non-cancelled ordered quantity since the sync
 * − live reservations of other carts. The derived status comes from the domain
 * calculator; raw quantities never leave the application layer (CLAUDE.md #5).
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private static final OffsetDateTime EPOCH = Instant.EPOCH.atOffset(ZoneOffset.UTC);
    /** Callers without a cart token: every live reservation counts as foreign. */
    public static final String NO_CART = "";

    private final OrderRepository orders;
    private final ReservationRepository reservations;
    private final WorkshopSessionRepository workshopSessions;

    private final StockStatusCalculator stockCalculator = new StockStatusCalculator();

    /** Ledger balance for a variant, ignoring the caller's own reservation. */
    public int availableQty(Variant variant, String ownCartToken) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String token = ownCartToken == null ? NO_CART : ownCartToken;

        // workshop seat: balance = capacity − non-cancelled bookings − foreign holds;
        // a session that has already started is not bookable
        Optional<WorkshopSession> session = workshopSessions.findByVariantId(variant.getId());
        if (session.isPresent()) {
            WorkshopSession s = session.get();
            if (!s.getStartAt().isAfter(now)) {
                return 0;
            }
            int booked = orders.orderedQuantitySince(variant.getId(), EPOCH, OrderStatus.CANCELLED);
            int held = reservations.activeForeignQuantity(variant.getId(), now, token);
            return s.getCapacity() - booked - held;
        }

        // physical stock: balance = last synced stock − orders since sync − foreign holds
        if (variant.getLastSyncQty() == null) {
            return 0;
        }
        OffsetDateTime since = variant.getLastSyncAt() != null ? variant.getLastSyncAt() : EPOCH;
        int ordered = orders.orderedQuantitySince(variant.getId(), since, OrderStatus.CANCELLED);
        int reserved = reservations.activeForeignQuantity(variant.getId(), now, token);
        return variant.getLastSyncQty() - ordered - reserved;
    }

    public StockAvailability availability(Variant variant, String ownCartToken) {
        return stockCalculator.evaluate(
                variant.getProduct().isDiscontinued(),
                variant.getManualAvailability(),
                variant.isManageStock(),
                availableQty(variant, ownCartToken),
                variant.getLowStockThreshold());
    }

    public boolean isOrderable(Variant variant, String ownCartToken) {
        StockStatus status = availability(variant, ownCartToken).status();
        return status == StockStatus.IN_STOCK || status == StockStatus.PREORDER;
    }

    /**
     * Hard check for order placement: orderable AND (when stock is managed and not
     * preorder) the requested quantity fits the ledger balance.
     */
    public boolean canFulfil(Variant variant, String ownCartToken, int requestedQty) {
        StockStatus status = availability(variant, ownCartToken).status();
        if (status == StockStatus.PREORDER) {
            return true;
        }
        if (status != StockStatus.IN_STOCK) {
            return false;
        }
        return !variant.isManageStock() || availableQty(variant, ownCartToken) >= requestedQty;
    }
}
