-- WooCommerce catalog PoC report (TASKS.md T1).
-- Read-only analysis of the legacy nemiskacat.hu WooCommerce database.
-- Table prefix: guxdop_  | WooCommerce classic storage (posts + postmeta).
--
-- Run against the running WordPress DB container (see README.md in this dir):
--   docker exec -i wp_db mysql -uroot -proot_password \
--     --default-character-set=utf8mb4 --table client7002dbnem < catalog-report.sql
--
-- IMPORTANT: always connect with --default-character-set=utf8mb4, otherwise
-- Hungarian text is shown as mojibake (the stored data itself is clean utf8mb4).

SELECT '== 1. PRODUCTS/VARIATIONS BY STATUS ==' AS '';
SELECT post_type, post_status, COUNT(*) cnt
FROM guxdop_posts WHERE post_type IN ('product','product_variation')
GROUP BY post_type, post_status WITH ROLLUP;

SELECT '== 2. PRODUCT TYPES ==' AS '';
SELECT t.name product_type, COUNT(*) cnt
FROM guxdop_posts p
JOIN guxdop_term_relationships tr ON tr.object_id=p.ID
JOIN guxdop_term_taxonomy tt ON tt.term_taxonomy_id=tr.term_taxonomy_id AND tt.taxonomy='product_type'
JOIN guxdop_terms t ON t.term_id=tt.term_id
WHERE p.post_type='product' GROUP BY t.name ORDER BY cnt DESC;

SELECT '== 3. SKU COVERAGE (published products) ==' AS '';
SELECT
 (SELECT COUNT(*) FROM guxdop_posts WHERE post_type='product' AND post_status='publish') published_products,
 (SELECT COUNT(*) FROM guxdop_postmeta pm JOIN guxdop_posts p ON p.ID=pm.post_id
   WHERE p.post_type='product' AND p.post_status='publish' AND pm.meta_key='_sku' AND pm.meta_value<>'') with_nonempty_sku;

SELECT '== 3b. DUPLICATE SKUs (top) ==' AS '';
SELECT meta_value sku, COUNT(*) c FROM guxdop_postmeta
WHERE meta_key='_sku' AND meta_value<>'' GROUP BY meta_value HAVING c>1 ORDER BY c DESC LIMIT 15;

SELECT '== 4. PRODUCT CATEGORIES ==' AS '';
SELECT COUNT(*) product_categories, SUM(parent=0) root_cats, SUM(parent<>0) child_cats
FROM guxdop_term_taxonomy WHERE taxonomy='product_cat';
SELECT t.name, tt.count FROM guxdop_term_taxonomy tt JOIN guxdop_terms t ON t.term_id=tt.term_id
WHERE tt.taxonomy='product_cat' ORDER BY tt.count DESC LIMIT 15;

SELECT '== 5. GLOBAL ATTRIBUTES (variation axes) ==' AS '';
SELECT attribute_name, attribute_label, attribute_type FROM guxdop_woocommerce_attribute_taxonomies;
SELECT pa.attribute_name, COUNT(DISTINCT pm.post_id) products
FROM guxdop_postmeta pm
JOIN (SELECT CONCAT('attribute_pa_', attribute_name) k, attribute_name FROM guxdop_woocommerce_attribute_taxonomies) pa
  ON pm.meta_key=pa.k
GROUP BY pa.attribute_name ORDER BY products DESC;

SELECT '== 6. PRICE/STOCK/IMAGE META COVERAGE (published products) ==' AS '';
SELECT pm.meta_key, COUNT(*) c
FROM guxdop_postmeta pm JOIN guxdop_posts p ON p.ID=pm.post_id
WHERE p.post_type='product' AND p.post_status='publish'
 AND pm.meta_key IN ('_price','_regular_price','_sale_price','_stock','_stock_status','_manage_stock','_sku','_thumbnail_id','_product_image_gallery','_weight','_tax_status','_tax_class')
GROUP BY pm.meta_key ORDER BY c DESC;

SELECT '== 6b. STOCK STATUS DISTRIBUTION (lookup) ==' AS '';
SELECT stock_status, COUNT(*) FROM guxdop_wc_product_meta_lookup GROUP BY stock_status;
SELECT COUNT(*) on_sale FROM guxdop_wc_product_meta_lookup WHERE onsale=1;

SELECT '== 7. ATTACHMENTS (images) & SEO (Yoast) ==' AS '';
SELECT (SELECT COUNT(*) FROM guxdop_posts WHERE post_type='attachment') total_attachments;
SELECT AVG(1+CHAR_LENGTH(meta_value)-CHAR_LENGTH(REPLACE(meta_value,',',''))) avg_gallery_imgs,
       MAX(1+CHAR_LENGTH(meta_value)-CHAR_LENGTH(REPLACE(meta_value,',',''))) max_gallery_imgs
FROM guxdop_postmeta WHERE meta_key='_product_image_gallery' AND meta_value<>'';
SELECT pm.meta_key, COUNT(*) c FROM guxdop_postmeta pm JOIN guxdop_posts p ON p.ID=pm.post_id
WHERE p.post_type='product' AND pm.meta_key IN ('_yoast_wpseo_title','_yoast_wpseo_metadesc','_yoast_wpseo_focuskw')
GROUP BY pm.meta_key;

SELECT '== 8. VARIATIONS-PER-PARENT DISTRIBUTION ==' AS '';
SELECT variation_count, COUNT(*) products FROM
 (SELECT post_parent, COUNT(*) variation_count FROM guxdop_posts WHERE post_type='product_variation' GROUP BY post_parent) x
GROUP BY variation_count ORDER BY variation_count;

SELECT '== 9. PRICE STORAGE SAMPLE ==' AS '';
SELECT DISTINCT lk.min_price FROM guxdop_wc_product_meta_lookup lk WHERE lk.min_price>0 ORDER BY lk.min_price LIMIT 8;
