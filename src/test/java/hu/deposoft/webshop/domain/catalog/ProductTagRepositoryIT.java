package hu.deposoft.webshop.domain.catalog;

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
class ProductTagRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired ProductTagRepository tags;

    @Test
    void savesAndFindsByExternalId() {
        tags.save(ProductTag.create(42L, "vintage", "Vintage"));
        assertThat(tags.findByExternalId(42L)).isPresent();
        assertThat(tags.findByExternalId(42L).orElseThrow().getSlug()).isEqualTo("vintage");
    }

    @Test
    void manualTagHasNullExternalIdAndIsFoundBySlug() {
        tags.save(ProductTag.createManual("kezi-cimke", "Kézi címke"));
        assertThat(tags.existsBySlug("kezi-cimke")).isTrue();
        assertThat(tags.findAllByOrderByNameAsc()).extracting(ProductTag::getSlug).contains("kezi-cimke");
        assertThat(tags.findAllByOrderByNameAsc().stream()
                .filter(t -> t.getSlug().equals("kezi-cimke")).findFirst().orElseThrow()
                .getExternalId()).isNull();
    }
}
