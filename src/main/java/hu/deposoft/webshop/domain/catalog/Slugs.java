package hu.deposoft.webshop.domain.catalog;

import java.text.Normalizer;
import java.util.Locale;

/** Derives a URL/identifier slug from a human label. Pure & stateless. Used for admin-created
 *  attribute terms (AttributeValue slugs are immutable at create time and have no source slug). */
public final class Slugs {

    private Slugs() {}

    public static String slugify(String label) {
        if (label == null) {
            return "";
        }
        String noDiacritics = Normalizer.normalize(label, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noDiacritics.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
    }
}
