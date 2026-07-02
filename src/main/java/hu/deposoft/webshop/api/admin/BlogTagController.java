package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.blog.BlogTagAdminService;
import hu.deposoft.webshop.application.blog.BlogTagAdminService.RenameRequest;
import hu.deposoft.webshop.application.blog.BlogTagAdminService.TagUpsert;
import hu.deposoft.webshop.application.blog.BlogTagAdminService.TagView;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BlogTagController {

    private final BlogTagAdminService service;

    @GetMapping("/api/admin/blog/tags/{id}")
    public TagView getOne(@PathVariable Long id) { return service.getTag(id); }

    @GetMapping("/api/admin/blog/tags")
    public List<TagView> list(HttpServletResponse response) {
        List<TagView> all = service.list();
        response.setHeader("X-Total-Count", String.valueOf(all.size()));
        return all;
    }

    @PostMapping("/api/admin/blog/tags")
    public TagView create(@RequestBody TagUpsert cmd) { return service.create(cmd); }

    @PutMapping("/api/admin/blog/tags/{id}")
    public TagView rename(@PathVariable Long id, @RequestBody RenameRequest cmd) { return service.rename(id, cmd); }

    @DeleteMapping("/api/admin/blog/tags/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
