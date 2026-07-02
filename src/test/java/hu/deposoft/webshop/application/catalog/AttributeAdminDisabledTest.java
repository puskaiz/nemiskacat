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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "webshop.admin.product-editor-enabled=false")
@Testcontainers
@Transactional
class AttributeAdminDisabledTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired AttributeRepository attributeRepo;
    @Autowired AttributeAdminService attributeAdmin;

    @Test
    void rejectsWhenEditorDisabled() {
        var szin = attributeRepo.save(hu.deposoft.webshop.domain.catalog.Attribute.create(null, "szin", "Szín", "select"));
        assertThatThrownBy(() -> attributeAdmin.addValue(szin.getId(), "Piros"))
                .isInstanceOf(ProductAdminEditService.EditorDisabledException.class);
    }
}
