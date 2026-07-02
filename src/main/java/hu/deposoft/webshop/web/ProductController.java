package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.catalog.CatalogQueryService;
import hu.deposoft.webshop.application.catalog.CatalogQueryService.ProductPageView;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * Product page on the WP-preserved URL scheme (/product/{slug}/). Thin: loads the
 * view from the query service, sets the micro-cache header, renders. The HTML is
 * session-independent (CLAUDE.md #2) — no session is ever touched here.
 */
@Controller
@RequiredArgsConstructor
public class ProductController {

    static final String MICRO_CACHE = "public, max-age=0, s-maxage=60";

    private final CatalogQueryService catalog;

    @GetMapping({"/product/{slug}", "/product/{slug}/"})
    public String product(@PathVariable String slug, Model model, HttpServletResponse response) {
        ProductPageView view = catalog.productPage(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        response.setHeader("Cache-Control", MICRO_CACHE);
        model.addAttribute("product", view);
        return "product";
    }
}
