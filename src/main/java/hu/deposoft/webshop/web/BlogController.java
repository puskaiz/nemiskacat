package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.blog.BlogQueryService;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Thin, session-independent (cacheable) blog pages (CLAUDE.md #2). */
@Controller
@RequiredArgsConstructor
public class BlogController {

    private final BlogQueryService blog;
    private final SidebarQueryService sidebarQuery;

    /** Available to every blog view; the sidebar fragment reads ${sidebar}. */
    @ModelAttribute("sidebar")
    public SidebarQueryService.SidebarView sidebar() {
        return sidebarQuery.sidebar();
    }

    @GetMapping({"/blog", "/blog/"})
    public String list(@RequestParam(defaultValue = "1") int page, Model model) {
        model.addAttribute("list", blog.publishedList(page));
        return "blog/list";
    }

    @GetMapping({"/blog/kategoria/{slug}", "/blog/kategoria/{slug}/"})
    public String category(@PathVariable String slug,
                           @RequestParam(defaultValue = "1") int page, Model model) {
        var view = blog.publishedListByCategory(slug, page)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("list", view);
        return "blog/list";
    }

}
