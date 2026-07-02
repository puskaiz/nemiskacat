package hu.deposoft.webshop.application.customer;

import hu.deposoft.webshop.domain.customer.Customer;
import hu.deposoft.webshop.domain.customer.CustomerRepository;
import hu.deposoft.webshop.domain.customer.CustomerRole;
import hu.deposoft.webshop.integrations.wordpress.SourceCustomer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Idempotent customer migration (T13): upserts by wp_user_id. Phpass hashes are
 * stored with the {wp} delegating-encoder prefix so login routes to the
 * WordPress encoder and upgrades to bcrypt on success. Rows whose email already
 * belongs to a different account are skipped (logged) to keep email unique.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerImporter {

    private final CustomerRepository customers;

    public record Report(int created, int updated, int skipped) {
    }

    @Transactional
    public Report run(List<SourceCustomer> sources) {
        int created = 0, updated = 0, skipped = 0;
        for (SourceCustomer src : sources) {
            String email = Customer.normalizeEmail(src.email());
            if (email == null || email.isBlank() || src.passwordHash() == null) {
                skipped++;
                continue;
            }
            String storedHash = "{wp}" + src.passwordHash();
            Customer existing = customers.findByWpUserId(src.wpUserId()).orElse(null);
            if (existing != null) {
                existing.updateProfile(src.firstName(), src.lastName(), src.displayName());
                existing.assignUsername(src.username());
                existing.assignRole(CustomerRole.fromWordPress(src.role()));
                updated++;
                continue;
            }
            if (customers.findByEmailIgnoreCase(email).isPresent()) {
                log.warn("Skipping wp_user_id={} — email already migrated to another account", src.wpUserId());
                skipped++;
                continue;
            }
            customers.save(Customer.migrated(src.wpUserId(), src.username(), email, storedHash,
                    src.firstName(), src.lastName(), src.displayName(),
                    CustomerRole.fromWordPress(src.role())));
            created++;
        }
        log.info("Customer import finished: created={}, updated={}, skipped={}", created, updated, skipped);
        return new Report(created, updated, skipped);
    }
}
