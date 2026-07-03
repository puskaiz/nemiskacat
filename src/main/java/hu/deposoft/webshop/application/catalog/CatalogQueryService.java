package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.config.WebshopProperties;
import hu.deposoft.webshop.domain.catalog.AttributeValue;
import hu.deposoft.webshop.domain.catalog.Category;
import hu.deposoft.webshop.domain.catalog.CategoryRepository;
import hu.deposoft.webshop.domain.catalog.EffectivePrice;
import hu.deposoft.webshop.domain.catalog.PriceCalculator;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.ProductStatus;
import hu.deposoft.webshop.domain.catalog.ProductType;
import hu.deposoft.webshop.domain.catalog.StockAvailability;
import hu.deposoft.webshop.domain.catalog.StockStatus;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.workshop.WorkshopSession;
import hu.deposoft.webshop.domain.workshop.WorkshopSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Read-side assembly for the customer-facing catalog pages. Produces complete,
 * session-independent view records (CLAUDE.md #2) — raw stock counts never leave
 * this service, only derived status (#5).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogQueryService {

    public static final int CATEGORY_PAGE_SIZE = 20;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final ObjectMapper objectMapper;
    private final WebshopProperties properties;
    private final AvailabilityService availabilityService;
    private final WorkshopSessionRepository workshopSessions;

    private final PriceCalculator priceCalculator = new PriceCalculator();

    /** Workshop dates are stored UTC, displayed in local time (CLAUDE.md #6). */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Budapest");
    private static final DateTimeFormatter SESSION_DATE =
            DateTimeFormatter.ofPattern("MMM d. EEEE HH:mm", Locale.of("hu"));

    // ---- views ----

    public record ImageView(String url, String alt) {
    }

    public record CategoryRef(String name, String slug) {
    }

    public record VariantView(String label, String sku, String priceFormatted, String regularFormatted,
                              boolean onSale, StockStatus status, String statusText, boolean orderable) {
    }

    public record ProductPageView(String name, String slug, String canonicalUrl, String title,
                                  String metaDescription, String shortDescriptionHtml, String descriptionHtml,
                                  List<CategoryRef> categories, List<ImageView> images,
                                  List<VariantView> variants, boolean singleVariant, String jsonLd,
                                  boolean workshop, List<WorkshopSessionView> sessions) {
    }

    /** One bookable workshop occurrence on the event page. The seat count is turned
     *  into a presentation label here — raw counts never reach the view (CLAUDE.md #5). */
    public record WorkshopSessionView(String sku, String dateLabel, String priceFormatted,
                                      String seatsText, boolean low, boolean soldOut, boolean orderable) {
    }

    public record WorkshopListItemView(String name, String url, String imageUrl,
                                       String nextDateLabel, String priceFromFormatted, boolean hasUpcoming) {
    }

    public record CategoryItemView(String name, String url, String imageUrl,
                                   String priceFromFormatted, boolean available,
                                   boolean addable, String sku) {
    }

    public record CategoryPageView(String name, String slug, String descriptionHtml,
                                   List<CategoryItemView> items, int page, int totalPages) {
    }

    /**
     * A top-level category card for the homepage grid. {@code iconKey} is the
     * handoff icon filename without extension (e.g. {@code "paint-can"}).
     */
    public record HomeCategoryView(String name, String slug, String url,
                                   long productCount, String iconKey) {
    }

    /** Assembled view for the homepage template. {@code nextWorkshop} may be null. */
    public record HomePageView(List<HomeCategoryView> categories,
                               List<CategoryItemView> featured,
                               WorkshopListItemView nextWorkshop) {
    }

    // ---- product page ----

    public Optional<ProductPageView> productPage(String slug) {
        return products.findBySlug(slug)
                .filter(p -> p.getStatus() == ProductStatus.PUBLISHED)
                .map(this::toProductView);
    }

    private ProductPageView toProductView(Product product) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String canonicalUrl = properties.canonicalBaseUrl() + "/product/" + product.getSlug() + "/";

        List<ImageView> imageViews = images.findByProductOrderByPositionAsc(product).stream()
                .map(i -> new ImageView(properties.imageUrl(i.getStorageKey()), i.getAlt()))
                .toList();

        List<VariantView> variantViews = product.getVariants().stream()
                .map(v -> toVariantView(product, v, now))
                .toList();

        String seoTitle = resolveSeo(product.getSeoTitle(), product);
        return new ProductPageView(
                product.getName(),
                product.getSlug(),
                canonicalUrl,
                seoTitle != null ? seoTitle : product.getName(),
                resolveSeo(product.getMetaDescription(), product),
                product.getShortDescription(),
                product.getDescription(),
                product.getCategories().stream().map(c -> new CategoryRef(c.getName(), c.getSlug())).toList(),
                imageViews,
                variantViews,
                variantViews.size() == 1,
                jsonLd(product, variantViews, imageViews, canonicalUrl, now),
                product.getType() == ProductType.WORKSHOP,
                product.getType() == ProductType.WORKSHOP ? sessionViews(product, now) : List.of());
    }

    // ---- workshops ----

    /** Upcoming sessions of a workshop, soonest first, with seat badges. */
    private List<WorkshopSessionView> sessionViews(Product product, OffsetDateTime now) {
        return product.getVariants().stream()
                .map(v -> workshopSessions.findByVariantId(v.getId())
                        .map(s -> Map.entry(v, s)).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(e -> e.getValue().getStartAt().isAfter(now))
                .sorted(Comparator.comparing(e -> e.getValue().getStartAt()))
                .map(e -> toSessionView(e.getKey(), e.getValue(), now))
                .toList();
    }

    private WorkshopSessionView toSessionView(Variant variant, WorkshopSession session, OffsetDateTime now) {
        int seats = availabilityService.availableQty(variant, AvailabilityService.NO_CART);
        boolean soldOut = seats <= 0;
        boolean low = !soldOut && seats <= variant.getLowStockThreshold();
        boolean orderable = variant.getSku() != null
                && availabilityService.isOrderable(variant, AvailabilityService.NO_CART);
        EffectivePrice price = priceCalculator.effective(variant.getRegularPriceHuf(), variant.getSalePriceHuf(),
                variant.getSaleFrom(), variant.getSaleTo(), now);
        String seatsText = soldOut ? "Betelt" : low ? "Utolsó pár hely!" : seats + " szabad hely";
        return new WorkshopSessionView(
                variant.getSku(),
                formatSessionDate(session.getStartAt()),
                price == null ? null : formatHuf(price.price().amount()),
                seatsText, low, soldOut, orderable);
    }

    /** All published workshops with their soonest upcoming date and from-price. */
    public List<WorkshopListItemView> workshopList() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return products.findByTypeAndStatus(ProductType.WORKSHOP, ProductStatus.PUBLISHED).stream()
                .map(p -> toWorkshopListItem(p, now))
                .sorted(Comparator.comparing((WorkshopListItemView i) -> !i.hasUpcoming())
                        .thenComparing(WorkshopListItemView::name))
                .toList();
    }

    // ---- homepage queries ----

    /**
     * Top-level categories (no parent) with their published product counts, ordered by
     * {@code sortOrder} then name. {@code iconKey} defaults to {@code "paint-can"} for
     * every category; the template can override per-slug if needed.
     */
    public List<HomeCategoryView> topLevelCategories() {
        return categories.findByParentIsNull(Sort.by("sortOrder", "name")).stream()
                .map(c -> new HomeCategoryView(
                        c.getName(),
                        c.getSlug(),
                        "/termekkategoria/" + c.getSlug() + "/",
                        products.countByCategories_IdAndStatus(c.getId(), ProductStatus.PUBLISHED),
                        "paint-can"))
                .toList();
    }

    /**
     * First {@code limit} published, available (in-stock or pre-order) simple/variable
     * products, as CategoryItemView cards. Reuses the existing internal mapper.
     */
    public List<CategoryItemView> featuredProducts(int limit) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return products.findPublishedByTypeIn(
                        ProductStatus.PUBLISHED,
                        List.of(ProductType.SIMPLE, ProductType.VARIABLE),
                        PageRequest.of(0, limit * 4))   // over-fetch, then filter available
                .stream()
                .map(p -> toCategoryItem(p, now))
                .filter(CategoryItemView::available)
                .limit(limit)
                .toList();
    }

    // ---- recommended-products (blog CMS) ----

    /**
     * Resolves each SKU to its product, keeps only PUBLISHED + available ones,
     * dedupes by product (when multiple SKUs map to the same product, the product
     * appears only once), and preserves the requested SKU order.
     * Out-of-stock and discontinued products are silently omitted.
     */
    public List<CategoryItemView> cardsBySkus(List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return List.of();
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        java.util.LinkedHashMap<Long, CategoryItemView> byProduct = new java.util.LinkedHashMap<>();
        for (String sku : skus) {
            products.findFirstByVariants_Sku(sku)
                    .filter(p -> p.getStatus() == ProductStatus.PUBLISHED)
                    .ifPresent(p -> byProduct.computeIfAbsent(p.getId(), id -> toCategoryItem(p, now)));
        }
        return byProduct.values().stream()
                .filter(CategoryItemView::available)
                .toList();
    }

    public boolean skuExists(String sku) {
        return products.existsByVariants_Sku(sku);
    }

    /**
     * The workshop with the soonest upcoming session, or empty when none has a future
     * session. Finds the earliest session across all published workshops and returns
     * the corresponding {@link WorkshopListItemView}.
     */
    public Optional<WorkshopListItemView> nextWorkshop() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // find the product whose earliest upcoming session is soonest
        return products.findByTypeAndStatus(ProductType.WORKSHOP, ProductStatus.PUBLISHED).stream()
                .map(p -> {
                    OffsetDateTime soonest = p.getVariants().stream()
                            .map(v -> workshopSessions.findByVariantId(v.getId()).orElse(null))
                            .filter(java.util.Objects::nonNull)
                            .map(WorkshopSession::getStartAt)
                            .filter(d -> d.isAfter(now))
                            .min(OffsetDateTime::compareTo)
                            .orElse(null);
                    return soonest != null ? Map.entry(soonest, p) : null;
                })
                .filter(java.util.Objects::nonNull)
                .min(Map.Entry.comparingByKey())
                .map(e -> toWorkshopListItem(e.getValue(), now));
    }

    /**
     * Workshop card for the homepage. Prefers the soonest upcoming workshop; when
     * none is scheduled, falls back to the most recent workshop (latest session
     * date) so the homepage always shows a workshop with its image. The fallback
     * carries no date label and {@code hasUpcoming=false}.
     */
    public Optional<WorkshopListItemView> featuredWorkshop() {
        Optional<WorkshopListItemView> upcoming = nextWorkshop();
        if (upcoming.isPresent()) {
            return upcoming;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return products.findByTypeAndStatus(ProductType.WORKSHOP, ProductStatus.PUBLISHED).stream()
                .map(p -> {
                    OffsetDateTime latest = p.getVariants().stream()
                            .map(v -> workshopSessions.findByVariantId(v.getId()).orElse(null))
                            .filter(java.util.Objects::nonNull)
                            .map(WorkshopSession::getStartAt)
                            .max(OffsetDateTime::compareTo)
                            .orElse(null);
                    return latest != null ? Map.entry(latest, p) : null;
                })
                .filter(java.util.Objects::nonNull)
                .max(Map.Entry.comparingByKey())
                .map(e -> toWorkshopListItem(e.getValue(), now));
    }

    private WorkshopListItemView toWorkshopListItem(Product product, OffsetDateTime now) {
        List<WorkshopSession> upcoming = product.getVariants().stream()
                .map(v -> workshopSessions.findByVariantId(v.getId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(s -> s.getStartAt().isAfter(now))
                .sorted(Comparator.comparing(WorkshopSession::getStartAt))
                .toList();
        Long priceFrom = product.getVariants().stream()
                .map(v -> priceCalculator.effective(v.getRegularPriceHuf(), v.getSalePriceHuf(),
                        v.getSaleFrom(), v.getSaleTo(), now))
                .filter(p -> p != null)
                .map(p -> p.price().amount())
                .min(Long::compare)
                .orElse(null);
        String imageUrl = images.findFirstByProductOrderByPositionAsc(product)
                .map(i -> properties.imageUrl(i.getStorageKey()))
                .orElse(null);
        return new WorkshopListItemView(
                product.getName(),
                "/product/" + product.getSlug() + "/",
                imageUrl,
                upcoming.isEmpty() ? null : formatSessionDate(upcoming.getFirst().getStartAt()),
                priceFrom == null ? null : formatHuf(priceFrom),
                !upcoming.isEmpty());
    }

    private String formatSessionDate(OffsetDateTime startAt) {
        return startAt.atZoneSameInstant(DISPLAY_ZONE).format(SESSION_DATE);
    }

    private VariantView toVariantView(Product product, Variant variant, OffsetDateTime now) {
        StockAvailability availability = availabilityOf(product, variant);
        EffectivePrice price = priceCalculator.effective(variant.getRegularPriceHuf(), variant.getSalePriceHuf(),
                variant.getSaleFrom(), variant.getSaleTo(), now);
        return new VariantView(
                variantLabel(variant),
                variant.getSku(),
                price == null ? null : formatHuf(price.price().amount()),
                price == null ? null : formatHuf(price.regular().amount()),
                price != null && price.onSale(),
                availability.status(),
                statusText(availability),
                availability.status() == StockStatus.IN_STOCK || availability.status() == StockStatus.PREORDER);
    }

    private StockAvailability availabilityOf(Product product, Variant variant) {
        return availabilityService.availability(variant, AvailabilityService.NO_CART);
    }

    private String variantLabel(Variant variant) {
        if (variant.getAttributeValues().isEmpty()) {
            return null;
        }
        return variant.getAttributeValues().stream()
                .sorted(Comparator.comparing(av -> av.getAttribute().getSlug()))
                .map(AttributeValue::getLabel)
                .reduce((a, b) -> a + " · " + b)
                .orElse(null);
    }

    // ---- category page ----

    public Optional<CategoryPageView> categoryPage(String slug, int page) {
        return categories.findBySlug(slug).map(category -> {
            Page<Product> productPage = products.findDistinctByCategories_SlugAndStatus(
                    slug, ProductStatus.PUBLISHED,
                    PageRequest.of(Math.max(0, page - 1), CATEGORY_PAGE_SIZE, Sort.by("name")));
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            List<CategoryItemView> items = productPage.getContent().stream()
                    .map(p -> toCategoryItem(p, now))
                    .toList();
            return new CategoryPageView(category.getName(), category.getSlug(), category.getDescription(),
                    items, page, Math.max(1, productPage.getTotalPages()));
        });
    }

    private CategoryItemView toCategoryItem(Product product, OffsetDateTime now) {
        Long priceFrom = product.getVariants().stream()
                .map(v -> priceCalculator.effective(v.getRegularPriceHuf(), v.getSalePriceHuf(),
                        v.getSaleFrom(), v.getSaleTo(), now))
                .filter(p -> p != null)
                .map(p -> p.price().amount())
                .min(Long::compare)
                .orElse(null);
        boolean available = product.getVariants().stream()
                .map(v -> availabilityOf(product, v).status())
                .anyMatch(s -> s == StockStatus.IN_STOCK || s == StockStatus.PREORDER);
        String imageUrl = images.findFirstByProductOrderByPositionAsc(product)
                .map(i -> properties.imageUrl(i.getStorageKey()))
                .orElse(null);
        // a simple, orderable product can be added straight from the card; everything
        // else (variable, or out of stock) sends the customer to the product page ("Opciók")
        boolean addable = false;
        String sku = null;
        if (product.getType() == ProductType.SIMPLE && product.getVariants().size() == 1) {
            Variant only = product.getVariants().getFirst();
            if (only.getSku() != null
                    && availabilityService.isOrderable(only, AvailabilityService.NO_CART)) {
                addable = true;
                sku = only.getSku();
            }
        }
        return new CategoryItemView(product.getName(), "/product/" + product.getSlug() + "/",
                imageUrl, priceFrom == null ? null : formatHuf(priceFrom), available, addable, sku);
    }

    // ---- JSON-LD ----

    private String jsonLd(Product product, List<VariantView> variants, List<ImageView> images,
                          String canonicalUrl, OffsetDateTime now) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org");
        root.put("@type", "Product");
        root.put("name", product.getName());
        if (!images.isEmpty()) {
            root.put("image", images.stream().map(ImageView::url).toList());
        }
        String description = resolveSeo(product.getMetaDescription(), product);
        if (description != null) {
            root.put("description", description);
        }

        List<Long> prices = new ArrayList<>();
        for (Variant v : product.getVariants()) {
            EffectivePrice p = priceCalculator.effective(v.getRegularPriceHuf(), v.getSalePriceHuf(),
                    v.getSaleFrom(), v.getSaleTo(), now);
            if (p != null) {
                prices.add(p.price().amount());
            }
        }
        boolean anyOrderable = variants.stream().anyMatch(VariantView::orderable);
        // A product with no variants (e.g. a workshop with no sessions yet) is not purchasable.
        StockStatus overallStatus = anyOrderable ? StockStatus.IN_STOCK
                : variants.isEmpty() ? StockStatus.OUT_OF_STOCK
                : variants.getFirst().status();
        String overallAvailability = schemaAvailability(new StockAvailability(overallStatus, false));

        Map<String, Object> offers = new LinkedHashMap<>();
        if (variants.size() == 1) {
            VariantView only = variants.getFirst();
            if (only.sku() != null) {
                root.put("sku", only.sku());
            }
            offers.put("@type", "Offer");
            offers.put("url", canonicalUrl);
            offers.put("priceCurrency", "HUF");
            if (!prices.isEmpty()) {
                offers.put("price", String.valueOf(prices.getFirst()));
            }
            offers.put("availability", schemaAvailability(
                    new StockAvailability(only.status(), false)));
        } else {
            offers.put("@type", "AggregateOffer");
            offers.put("url", canonicalUrl);
            offers.put("priceCurrency", "HUF");
            if (!prices.isEmpty()) {
                offers.put("lowPrice", String.valueOf(prices.stream().min(Long::compare).orElseThrow()));
                offers.put("highPrice", String.valueOf(prices.stream().max(Long::compare).orElseThrow()));
            }
            offers.put("offerCount", String.valueOf(variants.size()));
            offers.put("availability", overallAvailability);
        }
        root.put("offers", offers);
        return objectMapper.writeValueAsString(root);
    }

    private String schemaAvailability(StockAvailability availability) {
        return "https://schema.org/" + switch (availability.status()) {
            case IN_STOCK -> availability.lowStock() ? "LimitedAvailability" : "InStock";
            case OUT_OF_STOCK, TEMPORARILY_UNAVAILABLE -> "OutOfStock";
            case DISCONTINUED -> "Discontinued";
            case PREORDER -> "PreOrder";
        };
    }

    // ---- helpers ----

    private String statusText(StockAvailability availability) {
        return switch (availability.status()) {
            case IN_STOCK -> availability.lowStock() ? "Utolsó néhány darab" : "Készleten";
            case OUT_OF_STOCK -> "Elfogyott";
            case TEMPORARILY_UNAVAILABLE -> "Átmenetileg nem elérhető";
            case DISCONTINUED -> "Kifutott termék";
            case PREORDER -> "Előrendelhető";
        };
    }

    static String formatHuf(long amount) {
        return hu.deposoft.webshop.domain.catalog.Money.huf(amount).formatted();
    }

    /**
     * Resolves Yoast SEO template placeholders carried over from WordPress
     * (%%title%%, %%sitename%%, %%sep%%, %%page%%, %%category%%) into real values;
     * any other %%...%% token is stripped and whitespace/stray separators tidied.
     */
    private String resolveSeo(String template, Product product) {
        if (template == null || template.isBlank()) {
            return null;
        }
        String firstCategory = product.getCategories().isEmpty() ? ""
                : product.getCategories().iterator().next().getName();
        String out = template
                .replace("%%title%%", product.getName())
                .replace("%%sitename%%", properties.siteName())
                .replace("%%sep%%", "·")
                .replace("%%page%%", "")
                .replace("%%primary_category%%", firstCategory)
                .replace("%%category%%", firstCategory)
                .replaceAll("%%[^%]*%%", "")          // drop any remaining placeholder
                .replaceAll("\\s+", " ")
                .replaceAll("(?:\\s*·\\s*)+$", "")      // trailing separator
                .replaceAll("^(?:\\s*·\\s*)+", "")      // leading separator
                .trim();
        return out.isBlank() ? null : out;
    }
}
