package hu.deposoft.webshop.integrations.wordpress;

/** A WooCommerce/WordPress user as exported from the WP DB. role is the WP role key. */
public record SourceCustomer(
        long wpUserId,
        String username,
        String email,
        String passwordHash,
        String firstName,
        String lastName,
        String displayName,
        String role) {
}
