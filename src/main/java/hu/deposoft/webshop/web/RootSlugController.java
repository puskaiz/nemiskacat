package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.blog.BlogQueryService;
import hu.deposoft.webshop.application.page.PageQueryService;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owns the shared root {@code /{slug}} namespace. Resolves a content page first,
 * then a blog post, else 404. Session-independent, cacheable HTML (CLAUDE.md #2).
 * Exact routes (/blog, /product/{slug}, /termekkategoria/{slug}, /workshopok) are
 * more specific and still win over this catch-all.
 */
@Controller
@RequiredArgsConstructor
public class RootSlugController {

    private final PageQueryService pages;
    private final BlogQueryService blog;
    private final SidebarQueryService sidebarQuery;

    /** Blog post view needs ${sidebar}; content pages ignore it. */
    @ModelAttribute("sidebar")
    public SidebarQueryService.SidebarView sidebar() {
        return sidebarQuery.sidebar();
    }

    @GetMapping({"/{slug}", "/{slug}/"})
    public String bySlug(@PathVariable String slug, Model model, HttpServletResponse response) {
        var page = pages.getPublishedBySlug(slug);
        if (page.isPresent()) {
            response.setHeader("Cache-Control", ProductController.MICRO_CACHE);
            model.addAttribute("page", page.get());
            return "page";
        }
        var post = blog.getPublishedBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("post", post);
        return "blog/post";
    }
}
