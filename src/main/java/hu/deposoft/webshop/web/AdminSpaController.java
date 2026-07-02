package hu.deposoft.webshop.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Entry point for the admin SPA. The {@code /admin/**} resource handler
 * ({@link hu.deposoft.webshop.config.AdminSpaConfig}) serves the bundle and falls
 * back to {@code index.html} for deeper client routes, but it does not match the
 * bare {@code /admin} (and {@code /admin/}) path — so this forwards those to the
 * SPA shell, from where the browser router takes over.
 */
@Controller
public class AdminSpaController {

    @GetMapping({"/admin", "/admin/"})
    public String adminRoot() {
        return "forward:/admin/index.html";
    }
}
