package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.ProductAdminEditService;
import hu.deposoft.webshop.application.catalog.ProductAdminQueryService;
import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.PageResult;
import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.ProductDetailView;
import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.ProductSummary;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only product + category views for the admin SPA (P1). ADMIN-gated by SecurityConfig. */
@RestController
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductAdminQueryService query;
    private final ProductAdminEditService editService;

    @GetMapping("/api/admin/products")
    public List<ProductSummary> list(
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletResponse response) {
        PageResult result = query.list(category, q, page, size);
        response.setHeader("X-Total-Count", String.valueOf(result.total()));
        return result.items();
    }

    @GetMapping("/api/admin/products/{id}")
    public ProductDetailView detail(@PathVariable Long id) {
        return query.detail(id);
    }

    @org.springframework.web.bind.annotation.PutMapping("/api/admin/products/{id}")
    public ProductDetailView update(@PathVariable Long id,
                                    @org.springframework.web.bind.annotation.RequestBody ProductAdminEditService.ContentUpdate cmd) {
        return editService.updateContent(id, cmd);
    }
}
