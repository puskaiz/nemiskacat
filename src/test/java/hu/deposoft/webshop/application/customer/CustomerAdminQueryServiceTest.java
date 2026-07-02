package hu.deposoft.webshop.application.customer;

import hu.deposoft.webshop.application.customer.CustomerAdminQueryService.PageResult;
import hu.deposoft.webshop.domain.customer.Customer;
import hu.deposoft.webshop.domain.customer.CustomerRepository;
import hu.deposoft.webshop.domain.customer.CustomerRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/** Unit test: pagination/sort wiring + Customer -> CustomerSummary mapping incl. fullName() fallback. */
@ExtendWith(MockitoExtension.class)
class CustomerAdminQueryServiceTest {

    @Mock
    CustomerRepository customers;

    @InjectMocks
    CustomerAdminQueryService service;

    @Captor
    ArgumentCaptor<Pageable> pageableCaptor;

    @Captor
    ArgumentCaptor<String> qCaptor;

    @Test
    void passesPaginationAndNormalizesBlankQueryToNull() {
        when(customers.search(qCaptor.capture(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(2, 50, "   ");

        assertThat(qCaptor.getValue()).isNull();
        Pageable used = pageableCaptor.getValue();
        assertThat(used.getPageNumber()).isEqualTo(2);
        assertThat(used.getPageSize()).isEqualTo(50);
        // Ordering lives in the native query (ORDER BY created_at DESC), not the Pageable.
        assertThat(used.getSort().isSorted()).isFalse();
    }

    @Test
    void trimsQuery() {
        when(customers.search(qCaptor.capture(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(0, 20, "  anna  ");

        assertThat(qCaptor.getValue()).isEqualTo("anna");
    }

    @Test
    void mapsCustomerToSummaryUsingFullName() {
        Customer c = Customer.migrated(900L, "anna", "anna@example.com", "{wp}x",
                "Anna", "Kovács", "Anna K.", CustomerRole.CUSTOMER);
        when(customers.search(isNull(), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(c), Pageable.unpaged(), 7));

        PageResult result = service.list(0, 20, null);

        assertThat(result.total()).isEqualTo(7);
        assertThat(result.items()).hasSize(1);
        var item = result.items().getFirst();
        assertThat(item.name()).isEqualTo("Anna Kovács");
        assertThat(item.email()).isEqualTo("anna@example.com");
        assertThat(item.role()).isEqualTo(CustomerRole.CUSTOMER);
        assertThat(item.enabled()).isTrue();
    }

    @Test
    void fullNameFallsBackToDisplayNameWhenFirstAndLastAreNull() {
        Customer c = Customer.migrated(901L, "noname", "noname@example.com", "{wp}x",
                null, null, "Display Only", CustomerRole.SUBSCRIBER);
        Page<Customer> page = new PageImpl<>(List.of(c));
        when(customers.search(isNull(), pageableCaptor.capture())).thenReturn(page);

        PageResult result = service.list(0, 20, null);

        assertThat(result.items().getFirst().name()).isEqualTo("Display Only");
        assertThat(result.items().getFirst().role()).isEqualTo(CustomerRole.SUBSCRIBER);
    }

    @Test
    void fullNameFallsBackToEmailWhenNamesAndDisplayNameAreNull() {
        Customer c = Customer.migrated(902L, "bare", "bare@example.com", "{wp}x",
                null, null, null, CustomerRole.CUSTOMER);
        when(customers.search(isNull(), pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of(c)));

        PageResult result = service.list(0, 20, null);

        assertThat(result.items().getFirst().name()).isEqualTo("bare@example.com");
    }
}
