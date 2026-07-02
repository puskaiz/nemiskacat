package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException;
import hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException;
import hu.deposoft.webshop.config.AdminProperties;
import hu.deposoft.webshop.domain.catalog.AttributeValue;
import hu.deposoft.webshop.domain.catalog.AttributeValueRepository;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VariantRepository;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flag-gated variant management (P2b-2): create/update/delete/reorder with SKU-uniqueness,
 *  attribute-combination resolution + uniqueness, and the "keep >=1 variant" rule. Variant
 *  entities are managed within the transaction (dirty-checked); new ones are saved explicitly. */
@Service
@RequiredArgsConstructor
public class ProductVariantService {

    public record CreateVariant(String sku, List<Long> attributeValueIds) {}
    public record UpdateVariant(String sku, List<Long> attributeValueIds) {}

    private final ProductRepository products;
    private final VariantRepository variants;
    private final AttributeValueRepository attributeValues;
    private final ProductAdminQueryService query;
    private final AdminProperties adminProperties;

    @Transactional
    public ProductAdminQueryService.ProductDetailView createVariant(Long productId, CreateVariant cmd) {
        guard();
        Product p = product(productId);
        String sku = normalizeSku(cmd.sku());
        if (sku != null) requireSkuFree(sku, null);
        Set<AttributeValue> combo = resolveCombo(cmd.attributeValueIds());
        requireComboUnique(p, combo, null);
        int nextPos = p.getVariants().stream().mapToInt(Variant::getPosition).max().orElse(-1) + 1;
        Variant v = Variant.create(p, null, false);
        v.setSku(sku);
        v.setPosition(nextPos);
        v.replaceAttributeValues(combo);
        p.addVariant(v);
        variants.save(v);
        return query.detail(productId);
    }

    @Transactional
    public ProductAdminQueryService.ProductDetailView updateVariant(Long productId, Long variantId, UpdateVariant cmd) {
        guard();
        Product p = product(productId);
        Variant v = variantOf(p, variantId);
        String sku = normalizeSku(cmd.sku());
        if (sku != null) requireSkuFree(sku, variantId);
        Set<AttributeValue> combo = resolveCombo(cmd.attributeValueIds());
        requireComboUnique(p, combo, variantId);
        v.setSku(sku);
        v.replaceAttributeValues(combo);
        return query.detail(productId);
    }

    @Transactional
    public ProductAdminQueryService.ProductDetailView deleteVariant(Long productId, Long variantId) {
        guard();
        Product p = product(productId);
        Variant v = variantOf(p, variantId);
        if (p.getVariants().size() <= 1) {
            throw new IllegalArgumentException("A product must keep at least one variant");
        }
        p.removeVariant(v);
        reindex(p);
        return query.detail(productId);
    }

    @Transactional
    public ProductAdminQueryService.ProductDetailView reorderVariants(Long productId, List<Long> variantIds) {
        guard();
        Product p = product(productId);
        List<Variant> current = p.getVariants();
        if (variantIds.size() != current.size()
                || !variantIds.stream().sorted().toList().equals(current.stream().map(Variant::getId).sorted().toList())) {
            throw new IllegalArgumentException("Reorder list must contain exactly the product's variant ids");
        }
        for (int i = 0; i < variantIds.size(); i++) {
            Long id = variantIds.get(i);
            Variant v = current.stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
            v.setPosition(i);
        }
        return query.detail(productId);
    }

    private String normalizeSku(String sku) {
        if (sku == null) return null;
        String s = sku.trim();
        return s.isEmpty() ? null : s;
    }

    private void requireSkuFree(String sku, Long selfVariantId) {
        variants.findBySku(sku).ifPresent(existing -> {
            if (!existing.getId().equals(selfVariantId)) {
                throw new IllegalArgumentException("SKU already in use: " + sku);
            }
        });
    }

    private Set<AttributeValue> resolveCombo(List<Long> ids) {
        Set<AttributeValue> combo = new LinkedHashSet<>();
        if (ids == null) return combo;
        for (Long id : ids) {
            AttributeValue av = attributeValues.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown attribute value: " + id));
            combo.add(av);
        }
        long distinctAttributes = combo.stream().map(av -> av.getAttribute().getId()).distinct().count();
        if (distinctAttributes != combo.size()) {
            throw new IllegalArgumentException("A variant may have at most one value per attribute");
        }
        return combo;
    }

    private void requireComboUnique(Product p, Set<AttributeValue> combo, Long selfVariantId) {
        Set<Long> comboIds = combo.stream().map(AttributeValue::getId).collect(Collectors.toSet());
        for (Variant other : p.getVariants()) {
            if (other.getId() != null && other.getId().equals(selfVariantId)) continue;
            Set<Long> otherIds = other.getAttributeValues().stream().map(AttributeValue::getId).collect(Collectors.toSet());
            if (otherIds.equals(comboIds)) {
                throw new IllegalArgumentException("Another variant already has this attribute combination");
            }
        }
    }

    private void reindex(Product p) {
        List<Variant> ordered = p.getVariants().stream()
                .sorted(Comparator.comparingInt(Variant::getPosition)).toList();
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(i);
    }

    private Product product(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("No product " + id));
    }

    private Variant variantOf(Product p, Long variantId) {
        return p.getVariants().stream().filter(x -> x.getId().equals(variantId)).findFirst()
                .orElseThrow(() -> new NotFoundException("No variant " + variantId + " on product " + p.getId()));
    }

    private void guard() {
        if (!adminProperties.productEditorEnabled()) {
            throw new EditorDisabledException("Product editor is disabled");
        }
    }
}
