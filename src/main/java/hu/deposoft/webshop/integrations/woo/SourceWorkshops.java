package hu.deposoft.webshop.integrations.woo;

import java.util.List;

/**
 * Neutral import model: one snapshot of workshops to import, source-agnostic
 * (JSON export now). Mirrors {@link SourceCatalog}'s wrapper style — the export
 * file is {@code { "workshops": [ ... ] }}.
 */
public record SourceWorkshops(
        List<SourceWorkshop> workshops) {
}
