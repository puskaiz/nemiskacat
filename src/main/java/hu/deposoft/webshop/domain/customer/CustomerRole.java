package hu.deposoft.webshop.domain.customer;

/**
 * Account role, migrated 1:1 from WordPress. CUSTOMER/SUBSCRIBER are shop accounts;
 * ADMIN is staff (the future admin SPA). The Spring Security authority is
 * {@code ROLE_<name>}.
 */
public enum CustomerRole {
    CUSTOMER,
    SUBSCRIBER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    public static CustomerRole fromWordPress(String wpRole) {
        if (wpRole == null) {
            return CUSTOMER;
        }
        return switch (wpRole.toLowerCase()) {
            case "administrator", "admin" -> ADMIN;
            case "subscriber" -> SUBSCRIBER;
            default -> CUSTOMER;
        };
    }
}
