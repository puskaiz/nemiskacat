package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.ProductCategoryAdminService;
import hu.deposoft.webshop.application.catalog.ProductCategoryAdminService.CategoryUpsert;
import hu.deposoft.webshop.application.catalog.ProductCategoryAdminService.CategoryView;
import hu.deposoft.webshop.application.catalog.ProductCategoryAdminService.RenameRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryAdminService service;

    @GetMapping("/api/admin/categories/{id}")
    public CategoryView getOne(@PathVariable Long id) { return service.getById(id); }

    @GetMapping("/api/admin/categories")
    public List<CategoryView> list(HttpServletResponse response) {
        List<CategoryView> all = service.list();
        response.setHeader("X-Total-Count", String.valueOf(all.size()));
        return all;
    }

    @PostMapping("/api/admin/categories")
    public CategoryView create(@RequestBody CategoryUpsert cmd) { return service.create(cmd); }

    @PutMapping("/api/admin/categories/{id}")
    public CategoryView rename(@PathVariable Long id, @RequestBody RenameRequest cmd) { return service.rename(id, cmd); }

    @DeleteMapping("/api/admin/categories/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
