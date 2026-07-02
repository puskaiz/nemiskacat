package hu.deposoft.webshop.application.customer;

import hu.deposoft.webshop.domain.customer.Customer;
import hu.deposoft.webshop.domain.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service registration: new accounts get a {bcrypt} hash (via the delegating encoder). */
@Service
@RequiredArgsConstructor
public class CustomerRegistrationService {

    private final CustomerRepository customers;
    private final PasswordEncoder passwordEncoder;

    public static class EmailTakenException extends RuntimeException {
        public EmailTakenException(String email) {
            super("Email already registered: " + email);
        }
    }

    @Transactional
    public Customer register(String email, String rawPassword, String firstName, String lastName) {
        String normalized = Customer.normalizeEmail(email);
        customers.findByEmailIgnoreCase(normalized).ifPresent(c -> {
            throw new EmailTakenException(normalized);
        });
        Customer customer = Customer.create(normalized, passwordEncoder.encode(rawPassword));
        customer.updateProfile(firstName, lastName, null);
        return customers.save(customer);
    }
}
