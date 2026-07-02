package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException;
import hu.deposoft.webshop.config.WebshopProperties;
import hu.deposoft.webshop.domain.catalog.Attribute;
import hu.deposoft.webshop.domain.catalog.AttributeRepository;
import hu.deposoft.webshop.domain.catalog.AttributeValue;
import hu.deposoft.webshop.domain.catalog.Category;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.ProductStatus;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VatPricing;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only admin views over the real catalog (P1). Editing is P2/P3 (flag-gated). */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductAdminQueryService {

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final WebshopProperties properties;
    private final AttributeRepository attributeRepo;

    /** Hungarian-aware alphabetical ordering for category display (á, é, ö, ü …). */
    private static final Collator HU_COLLATOR = Collator.getInstance(new Locale("hu", "HU"));

    public record ProductSummary(Long id, String name, String slug, String primaryCategory,
                                 List<CategoryRef> categories,
                                 Long priceGrossHuf, int stockQty, ProductStatus status, int variantCount,
                                 String coverImageUrl, String sku) {}
    public record AttributeValueRef(Long id, Long attributeId, String attributeLabel, String valueLabel) {}
    public record AttributeValueOption(Long id, String slug, String label) {}
    public record AttributeView(Long id, String slug, String label, List<AttributeValueOption> values) {}
    public record VariantView(Long id, String label, String sku, Long regularPriceHuf, Long salePriceHuf,
                              int stockQty, int lowStockThreshold, List<AttributeValueRef> attributeValues) {}
    public record ImageView(Long id, String url, String alt) {}
    public record CategoryRef(String name, String slug) {}
    public record ProductDetailView(Long id, String name, String slug, ProductStatus status,
                                    String shortDescription, String description, String seoTitle,
                                    String metaDescription, Integer vatRatePercent,
                                    Integer effectiveVatRatePercent,
                                    List<CategoryRef> categories, List<ImageView> images,
                                    List<VariantView> variants) {}
    public record PageResult(List<ProductSummary> items, long total) {}

    public PageResult list(List<String> categories, String q, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.clamp(size, 1, 100);
        List<String> cats = (categories == null ? List.<String>of() : categories).stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .toList();
        boolean all = cats.isEmpty();
        // IN (...) needs a non-empty list to stay valid even when the filter is off.
        List<String> slugs = all ? List.of("") : cats;
        String term = (q == null || q.isBlank()) ? null : q.trim();
        Page<Product> result = products.adminSearch(all, slugs, term, PageRequest.of(safePage, safeSize));
        List<ProductSummary> items = result.getContent().stream().map(this::toSummary).toList();
        return new PageResult(items, result.getTotalElements());
    }

    public ProductDetailView detail(Long id) {
        Product p = products.findById(id).orElseThrow(() -> new NotFoundException("No product " + id));
        List<CategoryRef> cats = p.getCategories().stream().map(c -> new CategoryRef(c.getName(), c.getSlug())).toList();
        List<ImageView> imgs = images.findByProductOrderByPositionAsc(p).stream()
                .map(i -> new ImageView(i.getId(), properties.imageUrl(i.getStorageKey()), i.getAlt())).toList();
        List<VariantView> variants = p.getVariants().stream()
                .sorted(Comparator.comparingInt(Variant::getPosition))
                .map(this::toVariant).toList();
        Integer effectiveRate = VatPricing.effectiveRatePercent(p.getVatRatePercent(), p.getTaxClass());
        return new ProductDetailView(p.getId(), p.getName(), p.getSlug(), p.getStatus(),
                p.getShortDescription(), p.getDescription(), p.getSeoTitle(), p.getMetaDescription(),
                p.getVatRatePercent(), effectiveRate, cats, imgs, variants);
    }

    private ProductSummary toSummary(Product p) {
        Variant def = defaultVariant(p);
        List<CategoryRef> cats = p.getCategories().stream()
                .sorted(Comparator.comparing(Category::getName, HU_COLLATOR))
                .map(c -> new CategoryRef(c.getName(), c.getSlug()))
                .toList();
        String primary = cats.isEmpty() ? "—" : cats.get(0).name();
        String cover = images.findFirstByProductOrderByPositionAsc(p)
                .map(i -> properties.imageUrl(i.getStorageKey())).orElse(null);
        return new ProductSummary(p.getId(), p.getName(), p.getSlug(), primary, cats,
                def == null ? null : def.getRegularPriceHuf(),
                def == null ? 0 : qty(def), p.getStatus(), p.getVariants().size(), cover,
                def == null ? null : def.getSku());
    }

    private VariantView toVariant(Variant v) {
        List<AttributeValueRef> avs = v.getAttributeValues().stream()
                .sorted(Comparator.comparing(av -> av.getAttribute().getSlug()))
                .map(av -> new AttributeValueRef(av.getId(), av.getAttribute().getId(),
                        av.getAttribute().getLabel(), av.getLabel()))
                .toList();
        return new VariantView(v.getId(), label(v), v.getSku(), v.getRegularPriceHuf(), v.getSalePriceHuf(),
                qty(v), v.getLowStockThreshold(), avs);
    }

    private Variant defaultVariant(Product p) {
        return p.getVariants().stream().filter(Variant::isDefault).findFirst()
                .orElseGet(() -> p.getVariants().stream().min(Comparator.comparingInt(Variant::getPosition)).orElse(null));
    }

    private int qty(Variant v) {
        return v.getLastSyncQty() == null ? 0 : v.getLastSyncQty();
    }

    /** Combo label (attribute values sorted by attribute slug, joined " · "); else "Alap"/SKU. */
    private String label(Variant v) {
        if (!v.getAttributeValues().isEmpty()) {
            return v.getAttributeValues().stream()
                    .sorted(Comparator.comparing(av -> av.getAttribute().getSlug()))
                    .map(AttributeValue::getLabel)
                    .reduce((a, b) -> a + " · " + b).orElse("");
        }
        return v.isDefault() || v.getSku() == null ? "Alap" : v.getSku();
    }

    public List<AttributeView> attributes() {
        return attributeRepo.findAllWithValues().stream()
                .sorted(Comparator.comparing(Attribute::getSlug))
                .map(a -> new AttributeView(a.getId(), a.getSlug(), a.getLabel(),
                        a.getValues().stream()
                                .sorted(Comparator.comparingInt(AttributeValue::getSortOrder))
                                .map(av -> new AttributeValueOption(av.getId(), av.getSlug(), av.getLabel()))
                                .toList()))
                .toList();
    }
}
