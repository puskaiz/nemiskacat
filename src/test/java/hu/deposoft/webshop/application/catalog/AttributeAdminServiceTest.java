package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.domain.catalog.AttributeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "webshop.admin.product-editor-enabled=true")
@Testcontainers
@Transactional
class AttributeAdminServiceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired AttributeRepository attributeRepo;
    @Autowired AttributeAdminService attributeAdmin;

    @Test
    void addsNewTermWithDerivedSlug() {
        var szin = attributeRepo.save(hu.deposoft.webshop.domain.catalog.Attribute.create(null, "szin", "Szín", "select"));
        var view = attributeAdmin.addValue(szin.getId(), "Piros árnyalat");
        assertThat(view.values()).extracting(ProductAdminQueryService.AttributeValueOption::slug).contains("piros-arnyalat");
        assertThat(view.values()).extracting(ProductAdminQueryService.AttributeValueOption::label).contains("Piros árnyalat");
    }

    @Test
    void addValueIsIdempotentOnExistingSlug() {
        var szin = attributeRepo.save(hu.deposoft.webshop.domain.catalog.Attribute.create(null, "szin", "Szín", "select"));
        attributeAdmin.addValue(szin.getId(), "Piros");
        var view = attributeAdmin.addValue(szin.getId(), "Piros");
        assertThat(view.values()).filteredOn(o -> o.slug().equals("piros")).hasSize(1);
    }

    @Test
    void unknownAttributeThrowsNotFound() {
        assertThatThrownBy(() -> attributeAdmin.addValue(999999L, "X"))
                .isInstanceOf(hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException.class);
    }

    @Test
    void blankLabelRejected() {
        var szin = attributeRepo.save(hu.deposoft.webshop.domain.catalog.Attribute.create(null, "szin", "Szín", "select"));
        assertThatThrownBy(() -> attributeAdmin.addValue(szin.getId(), "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
