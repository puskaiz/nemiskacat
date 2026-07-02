package hu.deposoft.webshop.domain.customer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression test for the prod failure {@code function lower(bytea) does not exist}.
 *
 * <p>The customer search query used a {@code :q is null} clause; that left {@code :q} with no SQL
 * type, so once PostgreSQL promoted the statement to a server-side prepared statement it resolved
 * the bind to {@code bytea}, breaking {@code lower(...)}. A short run never reaches the pgjdbc
 * default {@code prepareThreshold} of 5, which is why the original integration test passed while the
 * running app failed. {@code prepareThreshold=1} forces server-side preparation so the regression is
 * exercised. The fix routes the no-term case through {@code findAll(...)} (no {@code :q} at all).
 */
@SpringBootTest
@Testcontainers
@Transactional
class CustomerRepositorySearchTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void serverPrepareImmediately(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.data-source-properties.prepareThreshold", () -> "1");
    }

    @Autowired
    CustomerRepository customers;

    @Test
    void searchWithNullTermDoesNotFailUnderServerPreparedStatements() {
        assertThatCode(() -> customers.search(null, PageRequest.of(0, 20)).getContent())
                .doesNotThrowAnyException();
    }

    @Test
    void searchWithTermDoesNotFailUnderServerPreparedStatements() {
        assertThatCode(() -> customers.search("example", PageRequest.of(0, 20)).getContent())
                .doesNotThrowAnyException();
    }
}
