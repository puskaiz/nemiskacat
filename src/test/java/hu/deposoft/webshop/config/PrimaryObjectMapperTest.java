package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.catalog.CatalogQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the local-profile startup failure: the in-house KHPos and
 * Billingo starters each register a {@code tools.jackson} ObjectMapper, so an
 * application bean that injects ObjectMapper sees more than one candidate. A
 * declared {@code @Primary} application mapper must keep those injections
 * unambiguous. The extra bean here stands in for the second starter mapper that
 * only the {@code local} profile activates.
 */
@SpringBootTest
@Testcontainers
class PrimaryObjectMapperTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @TestConfiguration
    static class SecondMapper {
        @Bean
        ObjectMapper anotherStarterMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Autowired
    CatalogQueryService catalog;

    @Test
    void injectionsStayUnambiguousWithMultipleMappers() {
        assertThat(catalog).isNotNull();
    }
}
