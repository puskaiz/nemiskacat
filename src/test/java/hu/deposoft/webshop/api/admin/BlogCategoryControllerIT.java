package hu.deposoft.webshop.api.admin;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@Testcontainers
@Transactional
class BlogCategoryControllerIT {

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
    void getByIdReturnsCategory() throws Exception {
        MvcResult created = mvc.perform(post("/api/admin/blog/categories").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Tech\",\"slug\":\"tech\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = node.get("id").asLong();

        mvc.perform(get("/api/admin/blog/categories/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Tech"))
                .andExpect(jsonPath("$.slug").value("tech"));
    }

    @Test
    void getByIdUnknownReturns404() throws Exception {
        mvc.perform(get("/api/admin/blog/categories/999999").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        MvcResult created = mvc.perform(post("/api/admin/blog/categories").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"ToDelete\",\"slug\":\"to-delete\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = node.get("id").asLong();

        mvc.perform(delete("/api/admin/blog/categories/" + id).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUnknownReturns404() throws Exception {
        mvc.perform(delete("/api/admin/blog/categories/999999").with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
