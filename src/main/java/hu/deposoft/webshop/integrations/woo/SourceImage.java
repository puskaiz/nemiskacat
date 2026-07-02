package hu.deposoft.webshop.integrations.woo;

public record SourceImage(
        long attachmentId,
        String storageKey,
        String alt,
        int position,
        boolean featured) {
}
