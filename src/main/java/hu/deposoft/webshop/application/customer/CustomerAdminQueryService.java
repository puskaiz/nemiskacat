package hu.deposoft.webshop.application.customer;

import hu.deposoft.webshop.domain.customer.Customer;
import hu.deposoft.webshop.domain.customer.CustomerRepository;
import hu.deposoft.webshop.domain.customer.CustomerRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** Read-only customer list for the admin SPA. ADMIN-gated by SecurityConfig. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerAdminQueryService {

    private final CustomerRepository customers;

    public record CustomerSummary(Long id, String name, String email, CustomerRole role, boolean enabled,
                                  OffsetDateTime createdAt) {
    }

    public record PageResult(List<CustomerSummary> items, long total) {
    }

    public PageResult list(int page, int size, String q) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        String term = (q == null || q.isBlank()) ? null : q.trim();
        // Newest-first ordering lives in the native query (see CustomerRepository.search).
        Page<Customer> result = customers.search(term, PageRequest.of(safePage, safeSize));
        List<CustomerSummary> items = result.getContent().stream()
                .map(c -> new CustomerSummary(c.getId(), c.fullName(), c.getEmail(), c.getRole(),
                        c.isEnabled(), c.getCreatedAt()))
                .toList();
        return new PageResult(items, result.getTotalElements());
    }
}
