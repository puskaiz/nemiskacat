package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.domain.catalog.Attribute;
import hu.deposoft.webshop.domain.catalog.AttributeRepository;
import hu.deposoft.webshop.domain.catalog.AttributeValue;
import hu.deposoft.webshop.domain.catalog.AttributeValueRepository;
import hu.deposoft.webshop.domain.catalog.Category;
import hu.deposoft.webshop.domain.catalog.CategoryRepository;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.ProductStatus;
import hu.deposoft.webshop.domain.catalog.ProductTag;
import hu.deposoft.webshop.domain.catalog.ProductTagRepository;
import hu.deposoft.webshop.domain.catalog.ProductType;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VariantRepository;
import hu.deposoft.webshop.application.content.InlineImageRewriter;
import hu.deposoft.webshop.config.WebshopProperties;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import hu.deposoft.webshop.integrations.woo.SourceTag;
import hu.deposoft.webshop.integrations.woo.SourceVariant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Idempotent catalog import (T4): upserts by external (source-system) id / SKU so
 * repeated runs produce no duplicates and pick up source-side changes. Slugs are
 * preserved on update (CLAUDE.md #7). The domain is source-agnostic; the mapping
 * from WooCommerce ids to {@code externalId} happens here.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CatalogImporter {

    private final CategoryRepository categories;
    private final AttributeRepository attributes;
    private final AttributeValueRepository attributeValues;
    private final ProductTagRepository productTags;
    private final ProductRepository products;
    private final VariantRepository variants;
    private final ProductImageRepository images;
    private final ImageFetcher imageFetcher;
    private final StorageService storage;
    private final WebshopProperties properties;
    private final InlineImageRewriter inlineImageRewriter;

    @Transactional
    public ImportReport run(SourceCatalog catalog) {
        ImportReport report = new ImportReport();
        OffsetDateTime importedAt = OffsetDateTime.now(ZoneOffset.UTC);

        Map<Long, Category> categoryByExternalId = upsertCategories(catalog, report);
        Map<Long, ProductTag> tagByExternalId = upsertTags(catalog, report);
        upsertAttributes(catalog, report);

        for (SourceProduct source : catalog.products()) {
            try {
                upsertProduct(source, categoryByExternalId, tagByExternalId, importedAt, report);
            } catch (RuntimeException e) {
                report.error("product woo_id=%d slug=%s: %s".formatted(source.wooId(), source.slug(), e.getMessage()));
                log.warn("Import failed for product woo_id={}", source.wooId(), e);
            }
        }

        log.info("Catalog import finished: {}", report);
        return report;
    }

    private Map<Long, Category> upsertCategories(SourceCatalog catalog, ImportReport report) {
        Map<Long, Category> byExternalId = new HashMap<>();
        // pass 1: upsert nodes
        for (SourceCategory sc : catalog.categories()) {
            Category category = categories.findByExternalId(sc.wooTermId()).orElse(null);
            if (category == null) {
                // A manually-created (or legacy) category may already hold this
                // slug (slug is UNIQUE). Adopt it into the imported set instead
                // of inserting a duplicate, which would violate the unique
                // constraint and abort the import — keeps the import idempotent
                // (CLAUDE.md rule #4).
                category = categories.findBySlug(sc.slug()).orElse(null);
                if (category == null) {
                    category = categories.save(Category.create(sc.wooTermId(), sc.slug(), sc.name()));
                    report.categoryCreated();
                } else {
                    category.assignExternalId(sc.wooTermId());
                    report.categoryUpdated();
                }
            } else {
                report.categoryUpdated();
            }
            category.setName(sc.name());
            category.setSortOrder(sc.sortOrder());
            category.setDescription(sc.description());
            category.setSeoTitle(sc.seoTitle());
            category.setMetaDescription(sc.metaDescription());
            byExternalId.put(sc.wooTermId(), category);
        }
        // pass 2: link parents
        for (SourceCategory sc : catalog.categories()) {
            if (sc.parentWooTermId() != null) {
                byExternalId.get(sc.wooTermId()).setParent(byExternalId.get(sc.parentWooTermId()));
            }
        }
        return byExternalId;
    }

    private Map<Long, ProductTag> upsertTags(SourceCatalog catalog, ImportReport report) {
        Map<Long, ProductTag> byExternalId = new HashMap<>();
        for (SourceTag st : nullToEmptyTags(catalog.tags())) {
            ProductTag tag = productTags.findByExternalId(st.wooTermId()).orElse(null);
            if (tag == null) {
                tag = productTags.save(ProductTag.create(st.wooTermId(), st.slug(), st.name()));
                report.tagCreated();
            } else {
                report.tagUpdated();
            }
            tag.setName(st.name());
            byExternalId.put(st.wooTermId(), tag);
        }
        return byExternalId;
    }

    private static java.util.List<SourceTag> nullToEmptyTags(java.util.List<SourceTag> l) {
        return l == null ? java.util.List.of() : l;
    }

    private void upsertAttributes(SourceCatalog catalog, ImportReport report) {
        for (var sa : catalog.attributes()) {
            Attribute attribute = attributes.findByExternalId(sa.wooAttributeId()).orElse(null);
            if (attribute == null) {
                attribute = attributes.save(Attribute.create(sa.wooAttributeId(), sa.slug(), sa.label(), sa.type()));
                report.attributeCreated();
            } else {
                attribute.setLabel(sa.label());
                attribute.setType(sa.type());
            }
            for (var sv : sa.values()) {
                Attribute owner = attribute;
                AttributeValue value = attributeValues.findByAttributeAndSlug(owner, sv.slug())
                        .orElseGet(() -> attributeValues.save(
                                AttributeValue.create(owner, sv.slug(), sv.label(), sv.sortOrder())));
                value.setLabel(sv.label());
                value.setSortOrder(sv.sortOrder());
            }
        }
    }

    private void upsertProduct(SourceProduct source, Map<Long, Category> categoryByExternalId,
                               Map<Long, ProductTag> tagByExternalId,
                               OffsetDateTime importedAt, ImportReport report) {
        ProductType type = "variable".equalsIgnoreCase(source.type()) ? ProductType.VARIABLE : ProductType.SIMPLE;
        ProductStatus status = "publish".equalsIgnoreCase(source.status()) ? ProductStatus.PUBLISHED : ProductStatus.DRAFT;

        Product product = products.findByExternalId(source.wooId()).orElse(null);
        boolean created = product == null;
        if (created) {
            product = products.save(Product.create(source.wooId(), source.slug(), source.name(), type, status));
            report.productCreated();
        } else {
            if (!product.getSlug().equals(source.slug())) {
                log.warn("Slug change ignored for woo_id={} (ours='{}', source='{}') — slugs are immutable",
                        source.wooId(), product.getSlug(), source.slug());
            }
            report.productUpdated();
        }
        product.setName(source.name());
        product.setStatus(status);
        product.setShortDescription(source.shortDescription());
        // Pull inline wp-content images into local storage and rewrite src to /media/…,
        // like the blog and page imports (CLAUDE.md #8) — never hot-link the source site.
        InlineImageRewriter.Result description = inlineImageRewriter.rewrite(source.description());
        product.setDescription(description.html());
        description.errors().forEach(e -> report.error(
                "product woo_id=%d description image: %s".formatted(source.wooId(), e)));
        product.setTaxClass(source.taxClass());
        product.setSeoTitle(source.seoTitle());
        product.setMetaDescription(source.metaDescription());

        Set<Category> cats = new LinkedHashSet<>();
        for (Long termId : source.categoryWooTermIds()) {
            Category c = categoryByExternalId.get(termId);
            if (c != null) {
                cats.add(c);
            } else {
                report.error("product woo_id=%d references unknown category woo_term_id=%d"
                        .formatted(source.wooId(), termId));
            }
        }
        product.replaceCategories(cats);

        Set<ProductTag> tagSet = new LinkedHashSet<>();
        for (Long termId : source.tagWooTermIds()) {
            ProductTag tg = tagByExternalId.get(termId);
            if (tg != null) tagSet.add(tg);
            else report.error("product woo_id=%d references unknown tag woo_term_id=%d"
                    .formatted(source.wooId(), termId));
        }
        product.replaceTags(tagSet);

        if (type == ProductType.SIMPLE) {
            upsertDefaultVariant(product, source, importedAt, report);
        } else {
            for (SourceVariant sv : source.variants()) {
                upsertVariation(product, sv, importedAt, report);
            }
        }

        for (var si : source.images()) {
            // The source carries the legacy wp/ key; download the bytes and store them
            // content-addressed (up/<hash>), like the workshop and blog imports, so the
            // shop serves the images itself instead of hot-linking the source site.
            // Idempotent: identical bytes yield the same key (CLAUDE.md #4, #8).
            String sourceUrl = properties.imageUrl(si.storageKey());
            try {
                ImageFetcher.FetchedImage fetched = imageFetcher.fetch(sourceUrl);
                String storageKey = storage.put(fetched.bytes(), fetched.contentType());
                ProductImage image = images.findByProductAndStorageKey(product, storageKey).orElse(null);
                if (image == null) {
                    images.save(ProductImage.create(product, storageKey, si.alt(), si.position(), si.featured()));
                    report.imageCreated();
                } else {
                    image.setAlt(si.alt());
                    image.setPosition(si.position());
                    image.setFeatured(si.featured());
                }
            } catch (RuntimeException e) {
                report.error("product woo_id=%d image %s: %s"
                        .formatted(source.wooId(), si.storageKey(), e.getMessage()));
                log.warn("Image import failed for product woo_id={} key={}",
                        source.wooId(), si.storageKey(), e);
            }
        }
    }

    private void upsertDefaultVariant(Product product, SourceProduct source,
                                      OffsetDateTime importedAt, ImportReport report) {
        Variant variant = variants.findByProductAndIsDefaultTrue(product).orElse(null);
        if (variant == null) {
            variant = Variant.create(product, null, true);
            product.addVariant(variant);
            report.variantCreated();
        } else {
            report.variantUpdated();
        }
        applySellingData(variant, source.sku(), source.regularPriceHuf(), source.salePriceHuf(),
                source.saleFrom(), source.saleTo(), source.manageStock(), source.weightGrams(), 0);
        variant.recordSyncedStock(source.stockQty(), importedAt);
        variants.save(variant);
    }

    private void upsertVariation(Product product, SourceVariant sv,
                                 OffsetDateTime importedAt, ImportReport report) {
        Variant variant = variants.findByExternalId(sv.wooId()).orElse(null);
        if (variant == null) {
            variant = Variant.create(product, sv.wooId(), false);
            product.addVariant(variant);
            report.variantCreated();
        } else {
            report.variantUpdated();
        }
        applySellingData(variant, sv.sku(), sv.regularPriceHuf(), sv.salePriceHuf(),
                sv.saleFrom(), sv.saleTo(), sv.manageStock(), sv.weightGrams(), sv.position());
        variant.recordSyncedStock(sv.stockQty(), importedAt);
        variant.replaceAttributeValues(resolveAttributeValues(sv.attributes(), report));
        variants.save(variant);
    }

    private void applySellingData(Variant variant, String sku, Long regularPriceHuf, Long salePriceHuf,
                                  OffsetDateTime saleFrom, OffsetDateTime saleTo,
                                  Boolean manageStock, Integer weightGrams, int position) {
        variant.setSku(sku);
        variant.setRegularPriceHuf(regularPriceHuf);
        variant.setSalePriceHuf(salePriceHuf);
        variant.setSaleFrom(saleFrom);
        variant.setSaleTo(saleTo);
        variant.setManageStock(manageStock == null || manageStock);
        variant.setWeightGrams(weightGrams);
        variant.setPosition(position);
    }

    private Set<AttributeValue> resolveAttributeValues(Map<String, String> attrs, ImportReport report) {
        Set<AttributeValue> values = new LinkedHashSet<>();
        for (var entry : attrs.entrySet()) {
            Attribute attribute = attributes.findBySlug(entry.getKey()).orElse(null);
            if (attribute == null) {
                report.error("unknown attribute slug '%s'".formatted(entry.getKey()));
                continue;
            }
            AttributeValue value = attributeValues.findByAttributeAndSlug(attribute, entry.getValue())
                    .orElseGet(() -> attributeValues.save(
                            AttributeValue.create(attribute, entry.getValue(), entry.getValue(), 0)));
            values.add(value);
        }
        return values;
    }
}
