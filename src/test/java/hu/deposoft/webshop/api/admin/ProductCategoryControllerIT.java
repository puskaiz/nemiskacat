package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.domain.catalog.Category;
import hu.deposoft.webshop.domain.catalog.CategoryRepository;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.ProductStatus;
import hu.deposoft.webshop.domain.catalog.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@Testcontainers
@Transactional
class ProductCategoryControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper objectMapper;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    MockMvc mvc;

    @BeforeEach
    void setUp() { mvc = webAppContextSetup(context).apply(springSecurity()).build(); }

    private RequestPostProcessor admin() { return user("admin@example.com").roles("ADMIN"); }

    @Test
    void unauthListReturns401() throws Exception {
        mvc.perform(get("/api/admin/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    void createRenameThenDelete() throws Exception {
        // POST → create category with slug "kezi"
        MvcResult result = mvc.perform(post("/api/admin/categories").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Kézi\",\"slug\":\"kezi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kézi"))
                .andExpect(jsonPath("$.slug").value("kezi"))
                .andReturn();

        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        long id = created.get("id").asLong();

        // PUT → rename; slug must remain "kezi"
        mvc.perform(put("/api/admin/categories/" + id).with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Átnevezett\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Átnevezett"))
                .andExpect(jsonPath("$.slug").value("kezi"));

        // DELETE → 204, then getOne → 404
        mvc.perform(delete("/api/admin/categories/" + id).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/categories/" + id).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankNameReturns400() throws Exception {
        mvc.perform(post("/api/admin/categories").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"   \",\"slug\":\"ures-nev\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateSlugReturns400() throws Exception {
        mvc.perform(post("/api/admin/categories").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Kézi\",\"slug\":\"kezi\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/categories").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Kézi2\",\"slug\":\"kezi\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidSlugReturns400() throws Exception {
        mvc.perform(post("/api/admin/categories").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"x\",\"slug\":\"Bad Slug!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renameUnknownReturns404() throws Exception {
        mvc.perform(put("/api/admin/categories/999999").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUnknownReturns404() throws Exception {
        mvc.perform(delete("/api/admin/categories/999999").with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listReturnsWithTotalCount() throws Exception {
        mvc.perform(post("/api/admin/categories").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Listás\",\"slug\":\"listas\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/categories").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"))
                .andExpect(jsonPath("$[?(@.slug == 'listas')]").exists());
    }

    @Test
    void getByIdReturnsCategory() throws Exception {
        MvcResult created = mvc.perform(post("/api/admin/categories").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Nyári\",\"slug\":\"nyari\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = node.get("id").asLong();

        mvc.perform(get("/api/admin/categories/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Nyári"))
                .andExpect(jsonPath("$.slug").value("nyari"));
    }

    @Test
    void getByIdUnknownReturns404() throws Exception {
        mvc.perform(get("/api/admin/categories/999999").with(admin()))
                .andExpect(status().isNotFound());
    }

    /**
     * A category referenced by a product must still delete: Product owns the product_category join,
     * so the service detaches the category from referencing products before deleting.
     */
    @Test
    void deleteReferencedCategorySucceeds() throws Exception {
        Category cat = categories.save(Category.create(null, "referalt", "Referált"));
        Product p = products.save(
                Product.create(null, "termek-referalt", "Termék", ProductType.SIMPLE, ProductStatus.PUBLISHED));
        p.replaceCategories(Set.of(cat));
        products.save(p);

        mvc.perform(delete("/api/admin/categories/" + cat.getId()).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/categories/" + cat.getId()).with(admin()))
                .andExpect(status().isNotFound());
    }
}
