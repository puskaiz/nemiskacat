package hu.deposoft.webshop.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The bare /admin entry must forward to the SPA shell (the resource handler only covers /admin/**). */
class AdminSpaControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AdminSpaController()).build();
    }

    @Test
    void adminRootForwardsToSpaShell() throws Exception {
        mvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/index.html"));
        mvc.perform(get("/admin/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/index.html"));
    }
}
