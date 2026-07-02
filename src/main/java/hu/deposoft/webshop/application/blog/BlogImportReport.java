package hu.deposoft.webshop.application.blog;

import java.util.ArrayList;
import java.util.List;

/** Mutable run report of a blog import; counters plus per-item errors. Mirrors
 *  {@code WorkshopImportReport}. */
public class BlogImportReport {

    private int postsCreated;
    private int postsUpdated;
    private int postsSkipped;
    private int categoriesCreated;
    private int tagsCreated;
    private int imagesStored;
    private final List<String> errors = new ArrayList<>();

    void postCreated() { postsCreated++; }
    void postUpdated() { postsUpdated++; }
    void postSkipped() { postsSkipped++; }
    void categoryCreated() { categoriesCreated++; }
    void tagCreated() { tagsCreated++; }
    void imageStored() { imagesStored++; }
    void error(String message) { errors.add(message); }

    public int postsCreated() { return postsCreated; }
    public int postsUpdated() { return postsUpdated; }
    public int postsSkipped() { return postsSkipped; }
    public int categoriesCreated() { return categoriesCreated; }
    public int tagsCreated() { return tagsCreated; }
    public int imagesStored() { return imagesStored; }
    public List<String> errors() { return List.copyOf(errors); }

    @Override
    public String toString() {
        return "BlogImportReport{posts=%d+%d (skipped %d), categories=%d, tags=%d, images=%d, errors=%d}"
                .formatted(postsCreated, postsUpdated, postsSkipped, categoriesCreated, tagsCreated, imagesStored, errors.size());
    }
}
