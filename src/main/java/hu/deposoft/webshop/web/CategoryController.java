package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.catalog.CatalogQueryService;
import hu.deposoft.webshop.application.catalog.CatalogQueryService.CategoryPageView;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Category listing on the WP-preserved URL scheme (/termekkategoria/{slug}/). */
@Controller
@RequiredArgsConstructor
public class CategoryController {

    private final CatalogQueryService catalog;

    @GetMapping({"/termekkategoria/{slug}", "/termekkategoria/{slug}/"})
    public String category(@PathVariable String slug,
                           @RequestParam(name = "page", defaultValue = "1") int page,
                           Model model, HttpServletResponse response) {
        CategoryPageView view = catalog.categoryPage(slug, page)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        response.setHeader("Cache-Control", ProductController.MICRO_CACHE);
        model.addAttribute("category", view);
        return "category";
    }
}
