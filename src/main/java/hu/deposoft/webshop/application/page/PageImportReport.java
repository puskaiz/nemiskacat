package hu.deposoft.webshop.application.page;

import java.util.ArrayList;
import java.util.List;

public class PageImportReport {
    private int created;
    private int updated;
    private int skipped;
    private int imagesStored;
    private final List<String> errors = new ArrayList<>();

    public void created() { created++; }
    public void updated() { updated++; }
    public void skipped() { skipped++; }
    public void imageStored() { imagesStored++; }
    public void error(String message) { errors.add(message); }
    public List<String> errors() { return errors; }

    @Override
    public String toString() {
        return "PageImportReport{created=%d, updated=%d, skipped=%d, imagesStored=%d, errors=%d}"
                .formatted(created, updated, skipped, imagesStored, errors.size());
    }
}
