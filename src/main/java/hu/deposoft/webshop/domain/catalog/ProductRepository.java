package hu.deposoft.webshop.domain.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByExternalId(Long externalId);

    Optional<Product> findBySlug(String slug);

    /**
     * Admin product search: optional category-slug (any-of) and name-substring filters, paginated.
     * DISTINCT because the category join can multiply rows. A product matches when it belongs to
     * ANY of the selected categories (OR semantics).
     *
     * <p>{@code :allCategories} short-circuits the category filter when no category is selected;
     * the {@code :categories} list must still be non-empty to keep the {@code IN (...)} clause
     * syntactically valid in PostgreSQL, so callers pass a single dummy slug in that case.
     *
     * <p>Native query rationale (same as {@code OrderRepository.search}): PostgreSQL cannot
     * infer the type of a bare {@code null} bind for the {@code :q} parameter in a
     * {@code :p IS NULL OR ...} filter (it defaults to {@code bytea}, so {@code lower(:q)} fails).
     * {@code CAST(:q AS text)} makes the type explicit. The {@code countQuery} must be kept in
     * sync with the main WHERE clause.
     */
    @Query(value = """
            SELECT DISTINCT p.* FROM product p
            LEFT JOIN product_category pc ON pc.product_id = p.id
            LEFT JOIN category c ON c.id = pc.category_id
            WHERE (:allCategories = TRUE OR c.slug IN (:categories))
              AND (CAST(:q AS text) IS NULL OR lower(p.name) LIKE lower(concat('%', CAST(:q AS text), '%')))
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p.id) FROM product p
            LEFT JOIN product_category pc ON pc.product_id = p.id
            LEFT JOIN category c ON c.id = pc.category_id
            WHERE (:allCategories = TRUE OR c.slug IN (:categories))
              AND (CAST(:q AS text) IS NULL OR lower(p.name) LIKE lower(concat('%', CAST(:q AS text), '%')))
            """,
            nativeQuery = true)
    Page<Product> adminSearch(@Param("allCategories") boolean allCategories,
                              @Param("categories") List<String> categories,
                              @Param("q") String q, Pageable pageable);

    Page<Product> findDistinctByCategories_SlugAndStatus(String categorySlug, ProductStatus status, Pageable pageable);

    List<Product> findByTypeAndStatus(ProductType type, ProductStatus status);

    List<Product> findByType(ProductType type);

    Optional<Product> findFirstByVariants_Sku(String sku);

    boolean existsByVariants_Sku(String sku);

    /** Count published products in a given category (by category id). */
    long countByCategories_IdAndStatus(Long categoryId, ProductStatus status);

    /** All products (any status) referencing a category, so the join can be cleared before delete. */
    List<Product> findByCategories_Id(Long categoryId);

    /**
     * First {@code limit} published simple/variable products ordered by name, for the homepage
     * featured row. Uses a JPQL query to avoid fetching the full table.
     */
    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.type IN :types ORDER BY p.name ASC")
    List<Product> findPublishedByTypeIn(@Param("status") ProductStatus status,
                                        @Param("types") List<ProductType> types,
                                        Pageable pageable);
}
