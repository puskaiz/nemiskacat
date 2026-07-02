package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.blog.BlogAdminService;
import hu.deposoft.webshop.application.blog.BlogAdminService.CategoryUpsert;
import hu.deposoft.webshop.application.blog.BlogAdminService.CategoryView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BlogCategoryController {

    private final BlogAdminService service;

    @GetMapping("/api/admin/blog/categories")
    public List<CategoryView> list() {
        return service.listCategories();
    }

    @GetMapping("/api/admin/blog/categories/{id}")
    public CategoryView getOne(@PathVariable Long id) {
        return service.getCategory(id);
    }

    @PostMapping("/api/admin/blog/categories")
    public CategoryView create(@RequestBody CategoryUpsert cmd) {
        return service.createCategory(cmd);
    }

    @PutMapping("/api/admin/blog/categories/{id}")
    public CategoryView update(@PathVariable Long id, @RequestBody CategoryUpsert cmd) {
        return service.updateCategory(id, cmd);
    }

    @DeleteMapping("/api/admin/blog/categories/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
