package hu.deposoft.webshop.integrations.woo;

/** A predefined value of a global attribute (a pa_* taxonomy term in Woo). */
public record SourceAttributeValue(
        String slug,
        String label,
        int sortOrder) {
}
