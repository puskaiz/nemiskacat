package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.domain.catalog.Attribute;
import hu.deposoft.webshop.domain.catalog.AttributeRepository;
import hu.deposoft.webshop.domain.catalog.AttributeValue;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = "webshop.admin.product-editor-enabled=true")
@Testcontainers
@Transactional
class AttributeControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    WebApplicationContext context;

    @Autowired
    CatalogImporter importer;

    @Autowired
    AttributeRepository attributeRepo;

    MockMvc mvc;

    Long szinId, pirosId, kekId;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint), List.of()));

        var szin = Attribute.create(null, "szin", "Szín", "select");
        var piros = AttributeValue.create(szin, "piros", "Piros", 0);
        var kek   = AttributeValue.create(szin, "kek", "Kék", 1);
        szin.getValues().add(piros); szin.getValues().add(kek);
        attributeRepo.save(szin);
        szinId = szin.getId(); pirosId = piros.getId(); kekId = kek.getId();
    }

    @Test void listsCatalog() throws Exception {
        mvc.perform(get("/api/admin/attributes").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.slug == 'szin')].values[*].label").value(org.hamcrest.Matchers.hasItem("Piros")));
    }

    @Test void addValue200() throws Exception {
        mvc.perform(post("/api/admin/attributes/" + szinId + "/values").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Sárga\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.values[*].slug").value(org.hamcrest.Matchers.hasItem("sarga")));
    }

    @Test void addValueForbiddenForNonAdmin() throws Exception {
        mvc.perform(post("/api/admin/attributes/" + szinId + "/values").with(user("u").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Sárga\"}"))
            .andExpect(status().isForbidden());
    }
}
