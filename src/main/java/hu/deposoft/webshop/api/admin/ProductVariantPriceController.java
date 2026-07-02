package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.ProductAdminQueryService.ProductDetailView;
import hu.deposoft.webshop.application.catalog.ProductVariantPriceService;
import hu.deposoft.webshop.application.catalog.ProductVariantPriceService.PriceUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin: per-variant price editing. Business rules live in ProductVariantPriceService. */
@RestController
@RequestMapping("/api/admin/products/{id}/variants/{variantId}")
@RequiredArgsConstructor
public class ProductVariantPriceController {

    private final ProductVariantPriceService priceService;

    @PutMapping("/price")
    public ProductDetailView updatePrice(@PathVariable Long id, @PathVariable Long variantId,
                                         @RequestBody PriceUpdate body) {
        return priceService.updatePrice(id, variantId, body);
    }
}
