package hu.deposoft.webshop.application.order;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mutable run report for the order import: counters plus diagnostic lists. */
public class OrderImportReport {

    private int imported;
    private int skipped;
    private int payments;
    private int orphanLines;
    private final Set<String> orphanSkus = new LinkedHashSet<>();
    private final Set<String> unknownStatuses = new LinkedHashSet<>();
    private final List<Long> nonHuf = new ArrayList<>();
    /** Orders whose own transaction failed (woo_order_id -> error); the rest still imported. */
    private final Map<Long, String> failures = new LinkedHashMap<>();

    void importedOrder() { imported++; }
    void skippedOrder() { skipped++; }
    void payment() { payments++; }
    void orphanLine(String sku) { orphanLines++; orphanSkus.add(sku == null ? "(no sku)" : sku); }
    void unknownStatus(String wooStatus) { unknownStatuses.add(wooStatus); }
    void nonHufOrder(long wooOrderId) { nonHuf.add(wooOrderId); }
    void failed(long wooOrderId, String message) { failures.put(wooOrderId, message); }

    public int imported() { return imported; }
    public int skipped() { return skipped; }
    public int payments() { return payments; }
    public int orphanLines() { return orphanLines; }
    public Set<String> orphanSkus() { return Set.copyOf(orphanSkus); }
    public Set<String> unknownStatuses() { return Set.copyOf(unknownStatuses); }
    public List<Long> nonHuf() { return List.copyOf(nonHuf); }
    public int failedCount() { return failures.size(); }
    public Map<Long, String> failures() { return Map.copyOf(failures); }

    @Override
    public String toString() {
        return ("OrderImportReport{imported=%d, skipped=%d, payments=%d, orphanLines=%d, "
                + "unknownStatuses=%d, nonHuf=%d, failed=%d}")
                .formatted(imported, skipped, payments, orphanLines,
                        unknownStatuses.size(), nonHuf.size(), failures.size());
    }
}
