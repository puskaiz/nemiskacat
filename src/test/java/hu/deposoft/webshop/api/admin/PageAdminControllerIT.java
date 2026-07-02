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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@Testcontainers
@Transactional
class PageAdminControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("admin@example.com").roles("ADMIN");
    }

    private static final String BODY = """
        {"slug":"rolunk","title":"Rólunk","bodyHtml":"<p>ok</p><script>x</script>",
         "seoTitle":null,"seoDescription":null}""";

    @Test
    void createSanitizesAndReturnsDraft() throws Exception {
        mvc.perform(post("/api/admin/pages").with(admin()).with(csrf())
                        .contentType("application/json").content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.bodyHtml").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>"))));
    }

    @Test
    void listSetsTotalCountHeader() throws Exception {
        mvc.perform(post("/api/admin/pages").with(admin()).with(csrf())
                .contentType("application/json").content(BODY)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/pages").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"));
    }

    @Test
    void duplicateSlugReturns409() throws Exception {
        mvc.perform(post("/api/admin/pages").with(admin()).with(csrf())
                .contentType("application/json").content(BODY)).andExpect(status().isOk());
        mvc.perform(post("/api/admin/pages").with(admin()).with(csrf())
                .contentType("application/json").content(BODY)).andExpect(status().isConflict());
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mvc.perform(get("/api/admin/pages/999999").with(admin())).andExpect(status().isNotFound());
    }

    private long createPage(String body) throws Exception {
        String resp = mvc.perform(post("/api/admin/pages").with(admin()).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(resp, "$.id")).longValue();
    }

    @Test
    void updateIgnoresSlugChangeAndKeepsOriginal() throws Exception {
        long id = createPage(BODY);
        String updateBody = """
            {"slug":"rolunk-uj","title":"Rólunk (frissítve)","bodyHtml":"<p>ok</p>",
             "seoTitle":null,"seoDescription":null}""";
        mvc.perform(put("/api/admin/pages/" + id).with(admin()).with(csrf())
                        .contentType("application/json").content(updateBody))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/pages/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("rolunk"))
                .andExpect(jsonPath("$.title").value("Rólunk (frissítve)"));
    }

    @Test
    void updateToExistingOtherSlugReturns409() throws Exception {
        long idA = createPage(BODY);
        String bodyB = """
            {"slug":"kapcsolat","title":"Kapcsolat","bodyHtml":"<p>ok</p>",
             "seoTitle":null,"seoDescription":null}""";
        long idB = createPage(bodyB);
        String updateBodyB = """
            {"slug":"rolunk","title":"Kapcsolat (frissítve)","bodyHtml":"<p>ok</p>",
             "seoTitle":null,"seoDescription":null}""";
        mvc.perform(put("/api/admin/pages/" + idB).with(admin()).with(csrf())
                        .contentType("application/json").content(updateBodyB))
                .andExpect(status().isConflict());
    }
}
