package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException;
import hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException;
import hu.deposoft.webshop.config.AdminProperties;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VatPricing;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flag-gated per-variant price editing (P2b-1): regular + sale (net or gross) with an optional
 *  sale window. Prices are stored GROSS; net inputs are converted via the product's effective VAT
 *  rate. Variant entities are managed within the transaction, so field writes persist on commit. */
@Service
@RequiredArgsConstructor
public class ProductVariantPriceService {

    public enum PriceBasis { NET, GROSS }
    public record PriceInput(long amount, PriceBasis basis) {}
    public record PriceUpdate(PriceInput regular, PriceInput sale,
                              OffsetDateTime saleFrom, OffsetDateTime saleTo) {}

    private final ProductRepository products;
    private final ProductAdminQueryService query;
    private final AdminProperties adminProperties;

    @Transactional
    public ProductAdminQueryService.ProductDetailView updatePrice(Long productId, Long variantId, PriceUpdate cmd) {
        guard();
        Product p = products.findById(productId)
                .orElseThrow(() -> new NotFoundException("No product " + productId));
        Variant v = p.getVariants().stream().filter(x -> x.getId().equals(variantId)).findFirst()
                .orElseThrow(() -> new NotFoundException("No variant " + variantId + " on product " + productId));

        int rate = VatPricing.effectiveRatePercent(p.getVatRatePercent(), p.getTaxClass());

        Long regularGross = grossOf(cmd.regular(), rate);
        if (regularGross == null) {
            v.setRegularPriceHuf(null);
            v.setSalePriceHuf(null);
            v.setSaleFrom(null);
            v.setSaleTo(null);
            return query.detail(productId);
        }

        Long saleGross = grossOf(cmd.sale(), rate);
        if (saleGross != null && saleGross > regularGross) {
            throw new IllegalArgumentException("Sale price must not exceed the regular price");
        }
        if (cmd.saleFrom() != null && cmd.saleTo() != null && cmd.saleFrom().isAfter(cmd.saleTo())) {
            throw new IllegalArgumentException("Sale window start must not be after its end");
        }

        v.setRegularPriceHuf(regularGross);
        if (saleGross == null) {
            v.setSalePriceHuf(null);
            v.setSaleFrom(null);
            v.setSaleTo(null);
        } else {
            v.setSalePriceHuf(saleGross);
            v.setSaleFrom(cmd.saleFrom());
            v.setSaleTo(cmd.saleTo());
        }
        return query.detail(productId);
    }

    private static Long grossOf(PriceInput in, int rate) {
        if (in == null) return null;
        if (in.amount() < 0) throw new IllegalArgumentException("Price must not be negative");
        return in.basis() == PriceBasis.GROSS ? in.amount() : VatPricing.toGross(in.amount(), rate);
    }

    private void guard() {
        if (!adminProperties.productEditorEnabled()) {
            throw new EditorDisabledException("Product editor is disabled");
        }
    }
}
