package hu.deposoft.webshop.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
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

@SpringBootTest
@Testcontainers
@Transactional
class BlogPostControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("admin@example.com").roles("ADMIN");
    }

    @Test
    void createReturnsDraftDetail() throws Exception {
        mvc.perform(post("/api/admin/blog/posts").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("""
                            {"slug":"api-cikk","title":"C","excerpt":"k","bodyHtml":"<p>B</p>",
                             "seoTitle":null,"seoDescription":null,"categorySlugs":[],"recommendedSkus":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void duplicateSlugReturns409() throws Exception {
        String body = """
            {"slug":"api-dup","title":"C","excerpt":"k","bodyHtml":"<p>b</p>",
             "seoTitle":null,"seoDescription":null,"categorySlugs":[],"recommendedSkus":[]}""";
        mvc.perform(post("/api/admin/blog/posts").with(admin()).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/blog/posts").with(admin()).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void getUnknownPostReturns404() throws Exception {
        mvc.perform(get("/api/admin/blog/posts/999999").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownSkuReturns422() throws Exception {
        mvc.perform(post("/api/admin/blog/posts").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("""
                            {"slug":"sku-test","title":"T","excerpt":"e","bodyHtml":"<p>b</p>",
                             "seoTitle":null,"seoDescription":null,"categorySlugs":[],"recommendedSkus":["NO-SUCH-SKU"]}"""))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createSanitizesBodyHtmlOnSave() throws Exception {
        // A disallowed element (iframe) must be stripped on save; the response body
        // is already the sanitized stored HTML.
        mvc.perform(post("/api/admin/blog/posts").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("""
                            {"slug":"sanitize-cikk","title":"S","excerpt":"k","bodyHtml":"<p>ok</p><iframe></iframe>",
                             "seoTitle":null,"seoDescription":null,"categorySlugs":[],"recommendedSkus":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyHtml").value("<p>ok</p>"));
    }
}
