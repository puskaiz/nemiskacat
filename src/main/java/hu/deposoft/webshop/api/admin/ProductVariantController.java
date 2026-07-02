package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.ProductDetailView;
import hu.deposoft.webshop.application.catalog.ProductVariantService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin: variant create/update/delete/reorder. Rules live in ProductVariantService. */
@RestController
@RequestMapping("/api/admin/products/{id}/variants")
@RequiredArgsConstructor
public class ProductVariantController {

    private final ProductVariantService service;

    public record ReorderRequest(List<Long> variantIds) {}

    @PostMapping
    public ProductDetailView create(@PathVariable Long id, @RequestBody ProductVariantService.CreateVariant body) {
        return service.createVariant(id, body);
    }

    @PutMapping("/{variantId}")
    public ProductDetailView update(@PathVariable Long id, @PathVariable Long variantId,
                                    @RequestBody ProductVariantService.UpdateVariant body) {
        return service.updateVariant(id, variantId, body);
    }

    @DeleteMapping("/{variantId}")
    public ProductDetailView delete(@PathVariable Long id, @PathVariable Long variantId) {
        return service.deleteVariant(id, variantId);
    }

    @PostMapping("/reorder")
    public ProductDetailView reorder(@PathVariable Long id, @RequestBody ReorderRequest body) {
        return service.reorderVariants(id, body.variantIds());
    }
}
