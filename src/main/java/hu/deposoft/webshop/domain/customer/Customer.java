package hu.deposoft.webshop.domain.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * A shop account. {@code passwordHash} is a Spring Security delegating-encoder
 * value: {@code {wp}$P$...} for a freshly migrated WooCommerce customer,
 * {@code {bcrypt}...} after the first login upgrades it (or for self-registered
 * accounts). {@code wpUserId} is the migration correlation key (null for
 * self-registered).
 */
@Entity
@Table(name = "customer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wp_user_id")
    private Long wpUserId;

    /** WordPress user_login; lets migrated customers log in by username too. Null for self-registered. */
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "display_name")
    private String displayName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerRole role = CustomerRole.CUSTOMER;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Customer create(String email, String passwordHash) {
        Customer c = new Customer();
        c.email = normalizeEmail(email);
        c.passwordHash = passwordHash;
        return c;
    }

    public static Customer migrated(Long wpUserId, String username, String email, String passwordHash,
                                    String firstName, String lastName, String displayName, CustomerRole role) {
        Customer c = create(email, passwordHash);
        c.wpUserId = wpUserId;
        c.username = username;
        c.firstName = firstName;
        c.lastName = lastName;
        c.displayName = displayName;
        c.role = role;
        return c;
    }

    public void assignUsername(String username) {
        this.username = username;
    }

    public void assignRole(CustomerRole role) {
        this.role = role;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Replaces the stored hash (login upgrade phpass -> bcrypt, or password change). */
    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public void updateProfile(String firstName, String lastName, String displayName) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName;
    }

    public String fullName() {
        String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return !name.isEmpty() ? name : (displayName != null ? displayName : email);
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
