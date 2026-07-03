package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.AppInfoService;
import hu.deposoft.webshop.application.blog.BlogQueryService;
import hu.deposoft.webshop.application.catalog.CatalogQueryService;
import hu.deposoft.webshop.application.catalog.CatalogQueryService.HomePageView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thin Thymeleaf controller for the {@code web} (customer-facing) layer. The
 * rendered HTML is session-independent and cacheable (CLAUDE.md rule #2): no
 * cart/user data is placed in the model here. Delegates any data to the
 * application service.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final AppInfoService appInfoService;
    private final CatalogQueryService catalogQueryService;
    private final BlogQueryService blogQueryService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("appName", appInfoService.currentInfo().name());
        model.addAttribute("home", new HomePageView(
                catalogQueryService.topLevelCategories(),
                catalogQueryService.featuredProducts(8),
                catalogQueryService.featuredWorkshop().orElse(null)));
        // Latest blog cards (template takes the first 3). Session-independent.
        model.addAttribute("latestPosts", blogQueryService.publishedList(1).items());
        return "index";
    }
}
