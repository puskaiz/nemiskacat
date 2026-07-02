package hu.deposoft.webshop.api;

import hu.deposoft.webshop.application.catalog.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@Testcontainers
class MediaControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void storageProps(DynamicPropertyRegistry registry) {
        registry.add("webshop.storage-dir", () -> storageDir.toString());
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    StorageService storage;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void servesStoredImageBytesPublicly() throws Exception {
        String key = storage.put("img-bytes".getBytes(), "image/png");
        mvc.perform(get("/media/" + key)) // no auth — public
                .andExpect(status().isOk())
                .andExpect(content().bytes("img-bytes".getBytes()));
    }

    @Test
    void unknownKeyReturns404() throws Exception {
        mvc.perform(get("/media/up/doesnotexist.png")).andExpect(status().isNotFound());
    }

    @Test
    void servesStoredPdfWithPdfContentType() throws Exception {
        // Linked PDFs (usage guides) pulled in from wp-content/uploads must serve as PDF,
        // not octet-stream, so the browser opens them inline.
        String key = storage.put("%PDF-1.4 fake".getBytes(), "application/pdf");
        org.assertj.core.api.Assertions.assertThat(key).endsWith(".pdf");
        mvc.perform(get("/media/" + key))
                .andExpect(status().isOk())
                .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_PDF));
    }
}
