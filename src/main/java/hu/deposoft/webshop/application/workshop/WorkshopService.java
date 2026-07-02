package hu.deposoft.webshop.application.workshop;

import hu.deposoft.webshop.domain.catalog.FulfilmentType;
import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.config.WebshopProperties;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.ProductStatus;
import hu.deposoft.webshop.domain.catalog.ProductType;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VariantRepository;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.workshop.WorkshopSession;
import hu.deposoft.webshop.domain.workshop.WorkshopSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Creates workshops and their sessions (T24). A workshop is a product
 * (type=WORKSHOP, invoiced via Billingo, fulfilment=EVENT); each session is a
 * variant with a linked {@link WorkshopSession} carrying its date + capacity.
 * Entered by hand in the admin.
 */
@Service
@RequiredArgsConstructor
public class WorkshopService {

    /** Seats at/below which the "last few seats" flag shows. */
    private static final int LOW_SEATS_THRESHOLD = 3;

    private final ProductRepository products;
    private final VariantRepository variants;
    private final WorkshopSessionRepository sessions;
    private final OrderRepository orders;
    private final ProductImageRepository images;
    private final WebshopProperties properties;

    /** Raised when cancelling a session whose seat is referenced by any order line. */
    public static class SessionHasBookingsException extends RuntimeException {
        public SessionHasBookingsException(String message) {
            super(message);
        }
    }

    /** Raised when deleting a workshop that still has booked sessions (order lines reference its seats). */
    public static class WorkshopHasBookingsException extends RuntimeException {
        public WorkshopHasBookingsException(String message) {
            super(message);
        }
    }

    /** A workshop/session id did not resolve. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
    }

    /** Gallery image view for a workshop (mirrors the product image gallery shape). */
    public record WorkshopImageView(Long id, String url, String alt, int position, boolean featured) {
    }

    @Transactional
    public Product createWorkshop(String name, String slug, String description, int vatRatePercent) {
        Product workshop = Product.create(null, slug, name, ProductType.WORKSHOP, ProductStatus.PUBLISHED);
        workshop.setDescription(description);
        workshop.setInvoiceSource(InvoiceSource.BILLINGO);
        workshop.setFulfilmentType(FulfilmentType.EVENT);
        workshop.setVatRatePercent(vatRatePercent);
        return products.save(workshop);
    }

    /** Adds a session (a sellable seat) to a workshop and returns its variant. */
    @Transactional
    public Variant addSession(Product workshop, OffsetDateTime startAt, int capacity, long priceHuf, String sku) {
        Variant seat = Variant.create(workshop, null, false);
        seat.setSku(sku);
        seat.setRegularPriceHuf(priceHuf);
        seat.setManageStock(true);
        seat.setLowStockThreshold(LOW_SEATS_THRESHOLD);
        seat.setPosition(workshop.getVariants().size());
        workshop.addVariant(seat);              // keep the in-memory relationship in sync (matches the importer)
        Variant saved = variants.save(seat);   // persist the seat directly (managed instance)
        sessions.save(WorkshopSession.create(saved, startAt, capacity));
        return saved;
    }

    /** Convenience overload: resolves the workshop by id, adds the session, re-queries and returns the session. */
    @Transactional
    public WorkshopSession addSession(Long workshopId, OffsetDateTime startAt, int capacity, long priceHuf, String sku) {
        Product ws = getWorkshop(workshopId);
        Variant seat = addSession(ws, startAt, capacity, priceHuf, sku);
        return sessions.findByVariantId(seat.getId()).orElseThrow(); // just persisted
    }

    @Transactional(readOnly = true)
    public List<Product> listWorkshops() {
        return products.findByType(ProductType.WORKSHOP);
    }

    @Transactional(readOnly = true)
    public Product getWorkshop(Long id) {
        Product p = products.findById(id).orElseThrow(() -> new NotFoundException("No workshop " + id));
        if (p.getType() != ProductType.WORKSHOP) {
            throw new NotFoundException("Product " + id + " is not a workshop");
        }
        return p;
    }

    @Transactional
    public Product updateWorkshop(Long id, String name, String slug, String description, int vatRatePercent) {
        return updateWorkshop(id, name, slug, description, vatRatePercent, null);
    }

    /**
     * Updates a workshop's editable fields. When {@code status} is non-null the publication
     * status is toggled (PUBLISHED/DRAFT); a null leaves the current status unchanged.
     */
    @Transactional
    public Product updateWorkshop(Long id, String name, String slug, String description,
                                  int vatRatePercent, ProductStatus status) {
        Product ws = getWorkshop(id);
        ws.setName(name);
        ws.updateSlug(slug);
        ws.setDescription(description);
        ws.setVatRatePercent(vatRatePercent);
        if (status != null) {
            ws.setStatus(status);
        }
        return ws;
    }

    /** Number of sessions (occurrences) attached to a workshop. */
    @Transactional(readOnly = true)
    public long sessionCount(Long workshopId) {
        return sessions.findByVariant_ProductIdOrderByStartAtAsc(workshopId).size();
    }

    /** The workshop's image gallery (ordered by position), reusing the product image rows. */
    @Transactional(readOnly = true)
    public List<WorkshopImageView> images(Product workshop) {
        return images.findByProductOrderByPositionAsc(workshop).stream()
                .map(i -> new WorkshopImageView(i.getId(), properties.imageUrl(i.getStorageKey()),
                        i.getAlt(), i.getPosition(), i.isFeatured()))
                .toList();
    }

    /**
     * Hard-deletes a workshop and everything it owns (sessions, seat variants, images, the product).
     * Guard: rejects with {@link WorkshopHasBookingsException} when ANY seat variant is referenced by
     * an order line (any status) — mirrors {@link #cancelSession}'s booking detection so order history
     * is never broken. Deletion happens in FK-safe order: sessions → images → product (variants are
     * removed via the product's orphanRemoval cascade).
     */
    @Transactional
    public void deleteWorkshop(Long id) {
        Product ws = getWorkshop(id);
        List<WorkshopSession> wsSessions = sessions.findByVariant_ProductIdOrderByStartAtAsc(id);
        for (WorkshopSession session : wsSessions) {
            int booked = orders.totalOrderedQuantity(session.getVariant().getId());
            if (booked > 0) {
                throw new WorkshopHasBookingsException(
                        "Workshop " + id + " has booked session(s) (" + booked
                                + " order line(s)); delete is blocked to preserve order history — cancel/refund the bookings first");
            }
        }
        // FK-safe order: remove session rows (FK: session.variant_id) before their seats.
        sessions.deleteAll(wsSessions);
        // Drop the image metadata rows (content-addressed blobs are safe to leave orphaned, CLAUDE.md #8).
        images.deleteAll(images.findByProductOrderByPositionAsc(ws));
        // Deleting the product cascades to its variants (orphanRemoval) and category links.
        products.delete(ws);
    }

    @Transactional(readOnly = true)
    public List<WorkshopSession> listSessions(Long workshopId) {
        getWorkshop(workshopId); // validates existence and type
        return sessions.findByVariant_ProductIdOrderByStartAtAsc(workshopId);
    }

    /** Attendee list for a workshop: who booked which session and how many seats (non-cancelled orders). */
    @Transactional(readOnly = true)
    public List<WorkshopBookingView> bookings(Long workshopId) {
        getWorkshop(workshopId);
        Map<Long, WorkshopSession> byVariant = listSessions(workshopId).stream()
                .collect(Collectors.toMap(s -> s.getVariant().getId(), s -> s));
        return orders.findWorkshopBookings(workshopId, OrderStatus.CANCELLED).stream()
                .map(oi -> {
                    WorkshopSession session = byVariant.get(oi.getVariant().getId());
                    var order = oi.getOrder();
                    return new WorkshopBookingView(
                            session != null ? session.getId() : null,
                            session != null ? session.getStartAt() : null,
                            oi.getSku(),
                            order.orderNumber(),
                            order.getStatus(),
                            order.getCustomerName(),
                            order.getEmail(),
                            order.getPhone(),
                            oi.getQuantity(),
                            oi.getId(),
                            oi.getCancelledQuantity(),
                            oi.getLineGrossHuf(),
                            oi.getUnitGrossHuf());
                })
                .toList();
    }

    @Transactional
    public WorkshopSession updateSession(Long sessionId, OffsetDateTime startAt, int capacity,
                                         long priceHuf, String sku) {
        WorkshopSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("No session " + sessionId));
        session.setStartAt(startAt);
        session.setCapacity(capacity);
        Variant seat = session.getVariant();
        seat.setRegularPriceHuf(priceHuf);
        seat.setSku(sku);
        return session;
    }

    /**
     * Cancels a session by its id. This hard-deletes the seat variant, so it rejects
     * with {@link SessionHasBookingsException} when the seat is referenced by ANY order
     * line (any status) — deleting it would break order history. Soft-cancel/refund of
     * sold sessions is slice 2.
     */
    @Transactional
    public void cancelSession(Long sessionId) {
        WorkshopSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("No session " + sessionId));
        Variant seat = session.getVariant();
        int booked = orders.totalOrderedQuantity(seat.getId());
        if (booked > 0) {
            throw new SessionHasBookingsException(
                    "Session " + sessionId + " has " + booked + " order line(s); deleting its seat would break order history — soft-cancel/refund is slice 2");
        }
        Product ws = seat.getProduct();
        sessions.delete(session);        // remove the referencing row first (FK: session.variant_id)
        ws.getVariants().remove(seat);   // orphanRemoval then deletes the seat
    }
}
