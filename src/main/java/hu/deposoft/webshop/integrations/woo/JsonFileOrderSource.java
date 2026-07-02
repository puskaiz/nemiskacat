package hu.deposoft.webshop.integrations.woo;

import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

/**
 * Reads a SourceOrder[] snapshot from a JSON file produced by
 * scripts/woo-export/export-orders.py. Mirrors JsonFileCatalogSource.
 */
public class JsonFileOrderSource implements OrderSource {

    private final ObjectMapper objectMapper;
    private final Path file;

    public JsonFileOrderSource(ObjectMapper objectMapper, Path file) {
        this.objectMapper = objectMapper;
        this.file = file;
    }

    @Override
    public List<SourceOrder> load() {
        SourceOrder[] orders = objectMapper.readValue(file.toFile(), SourceOrder[].class);
        return List.of(orders);
    }
}
