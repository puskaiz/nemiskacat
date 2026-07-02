package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.catalog.CatalogQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Workshop listing (T24 phase 4). The event page itself is served by
 * {@link ProductController} on the shared /product/{slug} scheme — a workshop is
 * a catalog product. The final /workshopok URL + 301 from the subdomain land in
 * phase 5; the page is session-independent, so it carries the micro-cache header.
 */
@Controller
@RequiredArgsConstructor
public class WorkshopController {

    private final CatalogQueryService catalog;

    @GetMapping({"/workshopok", "/workshopok/"})
    public String list(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", ProductController.MICRO_CACHE);
        model.addAttribute("workshops", catalog.workshopList());
        return "workshops";
    }
}
