package hu.deposoft.webshop.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Reproduces the admin "edit blog post → empty fields" bug.
 *
 * <p>Deliberately NOT {@code @Transactional}: a test-managed transaction would keep the
 * persistence context open during JSON serialization and mask the failure. With
 * {@code spring.jpa.open-in-view=false}, GET-by-id serialized the lazy {@code recommendedSkus}
 * {@code @ElementCollection} after the service transaction had already closed, throwing
 * {@code LazyInitializationException} (HTTP 500). The SPA's {@code useOne} then never received
 * the post, so the edit form rendered with empty fields.
 */
@SpringBootTest
@Testcontainers
class BlogPostGetByIdIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mvc() {
        return webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("admin@example.com").roles("ADMIN");
    }

    @Test
    void getByIdReturnsFullDetailAfterTransactionCloses() throws Exception {
        MockMvc mvc = mvc();

        MvcResult created = mvc.perform(post("/api/admin/blog/posts").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("""
                            {"slug":"get-by-id-cikk","title":"Cím","excerpt":"kivonat","bodyHtml":"<p>Törzs</p>",
                             "seoTitle":null,"seoDescription":null,"categorySlugs":[],"recommendedSkus":[]}"""))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = body.get("id").asLong();

        // GET runs in its own request: the service @Transactional commits and the session
        // closes BEFORE Jackson serializes the response. Lazy collections must be materialized.
        mvc.perform(get("/api/admin/blog/posts/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Cím"))
                .andExpect(jsonPath("$.bodyHtml").value("<p>Törzs</p>"))
                .andExpect(jsonPath("$.recommendedSkus").isArray())
                .andExpect(jsonPath("$.categorySlugs").isArray());
    }
}
