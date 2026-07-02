package hu.deposoft.webshop.application.audit;

import hu.deposoft.webshop.domain.order.AuditEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class AuditServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    AuditService audit;

    @Autowired
    AuditEntryRepository entries;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsTheAuthenticatedActor() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@example.com", "x",
                        AuthorityUtils.createAuthorityList("ROLE_ADMIN")));

        audit.record("WORKSHOP_CREATE", "workshop", "7", "Bútorfestés workshop");

        var found = entries.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("workshop", "7");
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getActor()).isEqualTo("admin@example.com");
        assertThat(found.getFirst().getAction()).isEqualTo("WORKSHOP_CREATE");
    }

    @Test
    void fallsBackToSystemWhenUnauthenticated() {
        audit.record("WORKSHOP_CREATE", "workshop", "8", "x");

        var found = entries.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("workshop", "8");
        assertThat(found.getFirst().getActor()).isEqualTo("system");
    }
}
