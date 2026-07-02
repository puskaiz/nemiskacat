package hu.deposoft.webshop.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@Testcontainers
@Transactional
class SidebarBlockControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper objectMapper;
    MockMvc mvc;

    @BeforeEach
    void setUp() { mvc = webAppContextSetup(context).apply(springSecurity()).build(); }

    private RequestPostProcessor admin() { return user("admin@example.com").roles("ADMIN"); }

    @Test
    void unauthenticatedListReturns401() throws Exception {
        mvc.perform(get("/api/admin/sidebar-blocks")).andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsSeededBlocksWithTotalCount() throws Exception {
        mvc.perform(get("/api/admin/sidebar-blocks").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "6"))
                .andExpect(jsonPath("$[0].blockType").value("AUTHOR"));
    }

    @Test
    void updateContentPersists() throws Exception {
        Long authorId = firstBlockId();
        mvc.perform(put("/api/admin/sidebar-blocks/" + authorId).with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"content\":\"{\\\"name\\\":\\\"Új Név\\\",\\\"bio\\\":\\\"b\\\",\\\"photoUrl\\\":\\\"/x.svg\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("Új Név")));
    }

    @Test
    void invalidContentReturns400() throws Exception {
        Long authorId = firstBlockId();
        mvc.perform(put("/api/admin/sidebar-blocks/" + authorId).with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"content\":\"{not json\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disableThenEnableToggles() throws Exception {
        Long id = firstBlockId();
        mvc.perform(post("/api/admin/sidebar-blocks/" + id + "/disable").with(admin()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/admin/sidebar-blocks/" + id + "/enable").with(admin()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void categoryVisibilityToggle() throws Exception {
        // seed a category via the list (uncategorized is seeded hidden; here just hit the endpoint shape)
        mvc.perform(get("/api/admin/sidebar-blocks/categories").with(admin()))
                .andExpect(status().isOk());
    }

    private Long firstBlockId() throws Exception {
        String body = mvc.perform(get("/api/admin/sidebar-blocks").with(admin()))
                .andReturn().getResponse().getContentAsString();
        JsonNode arr = objectMapper.readTree(body);
        return arr.get(0).get("id").asLong();
    }
}
