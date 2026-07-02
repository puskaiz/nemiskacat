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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@Testcontainers
@Transactional
class BlogTagControllerIT {

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
    void unauthListReturns401() throws Exception {
        mvc.perform(get("/api/admin/blog/tags")).andExpect(status().isUnauthorized());
    }

    @Test
    void createRenameThenDelete() throws Exception {
        // POST → create tag with slug "vintage"
        MvcResult result = mvc.perform(post("/api/admin/blog/tags").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Vintage\",\"slug\":\"vintage\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("vintage"))
                .andReturn();

        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        long id = created.get("id").asLong();

        // PUT → rename; slug must remain "vintage"
        mvc.perform(put("/api/admin/blog/tags/" + id).with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Retro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Retro"))
                .andExpect(jsonPath("$.slug").value("vintage"));

        // DELETE → 204
        mvc.perform(delete("/api/admin/blog/tags/" + id).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void duplicateSlugReturns400() throws Exception {
        mvc.perform(post("/api/admin/blog/tags").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Vintage\",\"slug\":\"vintage\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/admin/blog/tags").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Vintage2\",\"slug\":\"vintage\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidSlugReturns400() throws Exception {
        mvc.perform(post("/api/admin/blog/tags").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"x\",\"slug\":\"Bad Slug!\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renameUnknownReturns404() throws Exception {
        mvc.perform(put("/api/admin/blog/tags/999999").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankSlugReturns400() throws Exception {
        mvc.perform(post("/api/admin/blog/tags").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"X\",\"slug\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getByIdReturnsTag() throws Exception {
        MvcResult created = mvc.perform(post("/api/admin/blog/tags").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Travel\",\"slug\":\"travel\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = node.get("id").asLong();

        mvc.perform(get("/api/admin/blog/tags/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Travel"))
                .andExpect(jsonPath("$.slug").value("travel"));
    }

    @Test
    void getByIdUnknownReturns404() throws Exception {
        mvc.perform(get("/api/admin/blog/tags/999999").with(admin()))
                .andExpect(status().isNotFound());
    }
}
