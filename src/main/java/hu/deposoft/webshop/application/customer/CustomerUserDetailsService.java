package hu.deposoft.webshop.application.customer;

import hu.deposoft.webshop.domain.customer.Customer;
import hu.deposoft.webshop.domain.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads customers for Spring Security and performs upgrade-on-login: after a
 * successful authentication against a legacy {@code {wp}} phpass hash, Spring
 * Security calls {@link #updatePassword} with the freshly bcrypt-encoded value,
 * which we persist — so the migrated hash is replaced on first login.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerUserDetailsService implements UserDetailsService, UserDetailsPasswordService {

    private final CustomerRepository customers;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        // WooCommerce-style: the login identifier may be an email or the WP username
        Customer customer = customers.findByEmailIgnoreCase(login)
                .or(() -> customers.findByUsernameIgnoreCase(login))
                .orElseThrow(() -> new UsernameNotFoundException("No customer for '" + login + "'"));
        // principal is always the canonical email (so updatePassword + the account page resolve it)
        return User.withUsername(customer.getEmail())
                .password(customer.getPasswordHash())
                .authorities(AuthorityUtils.createAuthorityList(customer.getRole().authority()))
                .disabled(!customer.isEnabled())
                .build();
    }

    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails user, String newPassword) {
        customers.findByEmailIgnoreCase(user.getUsername()).ifPresent(customer -> {
            customer.changePasswordHash(newPassword);
            log.info("Upgraded stored password hash to bcrypt for customer {}", customer.getId());
        });
        return User.withUserDetails(user).password(newPassword).build();
    }
}
