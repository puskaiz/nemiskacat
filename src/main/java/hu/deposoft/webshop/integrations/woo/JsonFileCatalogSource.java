package hu.deposoft.webshop.integrations.woo;

import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

/**
 * Reads a {@link SourceCatalog} snapshot from a JSON file produced by
 * {@code scripts/woo-export/export.py}. The interim source until the WooCommerce
 * REST source arrives (REST key is [EMBER]-blocked).
 */
public class JsonFileCatalogSource implements CatalogSource {

    private final ObjectMapper objectMapper;
    private final Path file;

    public JsonFileCatalogSource(ObjectMapper objectMapper, Path file) {
        this.objectMapper = objectMapper;
        this.file = file;
    }

    @Override
    public SourceCatalog load() {
        // Jackson 3 throws unchecked JacksonException on parse/IO failure.
        return objectMapper.readValue(file.toFile(), SourceCatalog.class);
    }
}
