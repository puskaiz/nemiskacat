package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.ProductTagAdminService;
import hu.deposoft.webshop.application.catalog.ProductTagAdminService.RenameRequest;
import hu.deposoft.webshop.application.catalog.ProductTagAdminService.TagUpsert;
import hu.deposoft.webshop.application.catalog.ProductTagAdminService.TagView;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProductTagController {

    private final ProductTagAdminService service;

    @GetMapping("/api/admin/products/tags/{id}")
    public TagView getOne(@PathVariable Long id) { return service.getById(id); }

    @GetMapping("/api/admin/products/tags")
    public List<TagView> list(HttpServletResponse response) {
        List<TagView> all = service.list();
        response.setHeader("X-Total-Count", String.valueOf(all.size()));
        return all;
    }

    @PostMapping("/api/admin/products/tags")
    public TagView create(@RequestBody TagUpsert cmd) { return service.create(cmd); }

    @PutMapping("/api/admin/products/tags/{id}")
    public TagView rename(@PathVariable Long id, @RequestBody RenameRequest cmd) { return service.rename(id, cmd); }

    @DeleteMapping("/api/admin/products/tags/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
