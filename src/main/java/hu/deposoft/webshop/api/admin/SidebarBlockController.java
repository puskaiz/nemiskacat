package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.sidebar.SidebarAdminService;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.CategoryVisibility;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.ContentUpdate;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.ReorderRequest;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.SidebarBlockView;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.VisibilityUpdate;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SidebarBlockController {

    private final SidebarAdminService service;

    @GetMapping("/api/admin/sidebar-blocks")
    public List<SidebarBlockView> list(HttpServletResponse response) {
        List<SidebarBlockView> all = service.list();
        response.setHeader("X-Total-Count", String.valueOf(all.size()));
        return all;
    }

    @GetMapping("/api/admin/sidebar-blocks/{id}")
    public SidebarBlockView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/api/admin/sidebar-blocks/{id}")
    public SidebarBlockView update(@PathVariable Long id, @RequestBody ContentUpdate cmd) {
        return service.updateContent(id, cmd);
    }

    @PostMapping("/api/admin/sidebar-blocks/{id}/enable")
    public SidebarBlockView enable(@PathVariable Long id) {
        return service.setEnabled(id, true);
    }

    @PostMapping("/api/admin/sidebar-blocks/{id}/disable")
    public SidebarBlockView disable(@PathVariable Long id) {
        return service.setEnabled(id, false);
    }

    @PostMapping("/api/admin/sidebar-blocks/reorder")
    public List<SidebarBlockView> reorder(@RequestBody ReorderRequest body) {
        return service.reorder(body.blockIds());
    }

    @GetMapping("/api/admin/sidebar-blocks/categories")
    public List<CategoryVisibility> categories() {
        return service.categories();
    }

    @PostMapping("/api/admin/sidebar-blocks/categories/{slug}/visibility")
    public CategoryVisibility setVisibility(@PathVariable String slug, @RequestBody VisibilityUpdate body) {
        return service.setCategoryVisibility(slug, body.hidden());
    }
}
