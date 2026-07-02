package hu.deposoft.webshop.application.catalog;

import java.util.ArrayList;
import java.util.List;

/** Mutable run report of a catalog import; counters per entity plus per-item errors. */
public class ImportReport {

    private int categoriesCreated;
    private int categoriesUpdated;
    private int attributesCreated;
    private int tagsCreated;
    private int tagsUpdated;
    private int productsCreated;
    private int productsUpdated;
    private int variantsCreated;
    private int variantsUpdated;
    private int imagesCreated;
    private final List<String> errors = new ArrayList<>();

    void categoryCreated() {
        categoriesCreated++;
    }

    void categoryUpdated() {
        categoriesUpdated++;
    }

    void attributeCreated() {
        attributesCreated++;
    }

    void tagCreated() {
        tagsCreated++;
    }

    void tagUpdated() {
        tagsUpdated++;
    }

    void productCreated() {
        productsCreated++;
    }

    void productUpdated() {
        productsUpdated++;
    }

    void variantCreated() {
        variantsCreated++;
    }

    void variantUpdated() {
        variantsUpdated++;
    }

    void imageCreated() {
        imagesCreated++;
    }

    void error(String message) {
        errors.add(message);
    }

    public int categoriesCreated() {
        return categoriesCreated;
    }

    public int categoriesUpdated() {
        return categoriesUpdated;
    }

    public int attributesCreated() {
        return attributesCreated;
    }

    public int tagsCreated() {
        return tagsCreated;
    }

    public int tagsUpdated() {
        return tagsUpdated;
    }

    public int productsCreated() {
        return productsCreated;
    }

    public int productsUpdated() {
        return productsUpdated;
    }

    public int variantsCreated() {
        return variantsCreated;
    }

    public int variantsUpdated() {
        return variantsUpdated;
    }

    public int imagesCreated() {
        return imagesCreated;
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    @Override
    public String toString() {
        return "ImportReport{categories=%d+%d, tags=%d+%d, attributes=%d, products=%d+%d, variants=%d+%d, images=%d, errors=%d}"
                .formatted(categoriesCreated, categoriesUpdated, tagsCreated, tagsUpdated, attributesCreated,
                        productsCreated, productsUpdated, variantsCreated, variantsUpdated,
                        imagesCreated, errors.size());
    }
}
