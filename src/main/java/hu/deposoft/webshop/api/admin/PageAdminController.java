package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.page.PageAdminService;
import hu.deposoft.webshop.application.page.PageAdminService.PageDetail;
import hu.deposoft.webshop.application.page.PageAdminService.PageSummary;
import hu.deposoft.webshop.application.page.PageAdminService.PageUpsert;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PageAdminController {

    private final PageAdminService service;

    @GetMapping("/api/admin/pages")
    public List<PageSummary> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  HttpServletResponse response) {
        Page<PageSummary> result = service.list(page, size);
        response.setHeader("X-Total-Count", String.valueOf(result.getTotalElements()));
        return result.getContent();
    }

    @GetMapping("/api/admin/pages/{id}")
    public PageDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/api/admin/pages")
    public PageDetail create(@RequestBody PageUpsert cmd) {
        return service.create(cmd);
    }

    @PutMapping("/api/admin/pages/{id}")
    public PageDetail update(@PathVariable Long id, @RequestBody PageUpsert cmd) {
        return service.update(id, cmd);
    }

    @DeleteMapping("/api/admin/pages/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/api/admin/pages/{id}/publish")
    public PageDetail publish(@PathVariable Long id) {
        return service.publish(id);
    }

    @PostMapping("/api/admin/pages/{id}/unpublish")
    public PageDetail unpublish(@PathVariable Long id) {
        return service.unpublish(id);
    }
}
