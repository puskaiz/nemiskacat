package hu.deposoft.webshop.domain.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class AuditEntryRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    AuditEntryRepository audit;

    @Test
    void persistsAndQueriesByEntity() {
        audit.save(AuditEntry.of("admin@example.com", "WORKSHOP_CREATE", "workshop", "42", "Bútorfestés"));

        var found = audit.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("workshop", "42");

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getActor()).isEqualTo("admin@example.com");
        assertThat(found.getFirst().getCreatedAt()).isNotNull();
    }
}
