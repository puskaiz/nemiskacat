package hu.deposoft.webshop.application.workshop;

import java.util.ArrayList;
import java.util.List;

/** Mutable run report of a workshop import; counters plus per-item errors. Mirrors
 *  {@code ImportReport} from the catalog import. */
public class WorkshopImportReport {

    private int workshopsCreated;
    private int workshopsUpdated;
    private int imagesCreated;
    private final List<String> errors = new ArrayList<>();

    void workshopCreated() {
        workshopsCreated++;
    }

    void workshopUpdated() {
        workshopsUpdated++;
    }

    void imageCreated() {
        imagesCreated++;
    }

    void error(String message) {
        errors.add(message);
    }

    public int workshopsCreated() {
        return workshopsCreated;
    }

    public int workshopsUpdated() {
        return workshopsUpdated;
    }

    public int imagesCreated() {
        return imagesCreated;
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    @Override
    public String toString() {
        return "WorkshopImportReport{workshops=%d+%d, images=%d, errors=%d}"
                .formatted(workshopsCreated, workshopsUpdated, imagesCreated, errors.size());
    }
}
