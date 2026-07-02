#!/usr/bin/env python3
"""Export the WooCommerce catalog from the local wp_db container to JSON.

Produces a SourceCatalog snapshot (see integrations/woo DTOs) consumed by the
CatalogImporter via the `import` profile. Reads ONLY catalog tables — never
users/options/keys. Published products only.

Usage:
    python3 scripts/woo-export/export.py > /tmp/woo-catalog.json
"""

import json
import subprocess
import sys
from datetime import datetime, timezone

CONTAINER = "wp_db"
DB = "client7002dbnem"
PREFIX = "guxdop_"

META_KEYS = (
    "_sku", "_regular_price", "_sale_price", "_price",
    "_sale_price_dates_from", "_sale_price_dates_to",
    "_manage_stock", "_stock", "_weight", "_tax_class",
    "_thumbnail_id", "_product_image_gallery",
    "_yoast_wpseo_title", "_yoast_wpseo_metadesc",
)


def mysql_json_rows(query):
    """Run a query whose single column is a JSON_OBJECT per row; return parsed rows."""
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", "-proot_password",
         "--default-character-set=utf8mb4", "-B", "-N", "--raw", DB, "-e", query],
        capture_output=True, text=True, check=True)
    return [json.loads(line) for line in result.stdout.splitlines() if line.strip()]


def to_minor_huf(value):
    if value is None or value == "":
        return None
    return int(round(float(value)))


def to_int(value):
    if value is None or value == "":
        return None
    return int(float(value))


def to_grams(kg):
    if kg is None or kg == "":
        return None
    return int(round(float(kg) * 1000))


def unix_to_iso(value):
    if value is None or value == "":
        return None
    return datetime.fromtimestamp(int(float(value)), tz=timezone.utc).isoformat()


def nonempty(value):
    return value if value not in (None, "") else None


def main():
    t = lambda name: PREFIX + name

    categories = mysql_json_rows(f"""
        SELECT JSON_OBJECT(
          'wooTermId', t.term_id, 'slug', t.slug, 'name', t.name,
          'parentWooTermId', NULLIF(tt.parent, 0), 'sortOrder', 0,
          'description', NULLIF(tt.description, ''), 'seoTitle', NULL, 'metaDescription', NULL)
        FROM {t('term_taxonomy')} tt JOIN {t('terms')} t ON t.term_id = tt.term_id
        WHERE tt.taxonomy = 'product_cat'""")

    tags = mysql_json_rows(f"""
        SELECT JSON_OBJECT('wooTermId', t.term_id, 'slug', t.slug, 'name', t.name)
        FROM {t('term_taxonomy')} tt JOIN {t('terms')} t ON t.term_id = tt.term_id
        WHERE tt.taxonomy = 'product_tag'""")

    attributes = mysql_json_rows(f"""
        SELECT JSON_OBJECT(
          'wooAttributeId', attribute_id, 'slug', attribute_name,
          'label', attribute_label, 'type', attribute_type)
        FROM {t('woocommerce_attribute_taxonomies')}""")

    # pa_* taxonomy terms: the human-readable labels of attribute values
    attr_values = mysql_json_rows(f"""
        SELECT JSON_OBJECT(
          'attrSlug', SUBSTRING(tt.taxonomy, 4), 'slug', tm.slug, 'label', tm.name)
        FROM {t('term_taxonomy')} tt
        JOIN {t('terms')} tm ON tm.term_id = tt.term_id
        WHERE tt.taxonomy LIKE 'pa\\_%'""")
    values_by_attr = {}
    for av in attr_values:
        values_by_attr.setdefault(av["attrSlug"], []).append(av)
    for a in attributes:
        a["values"] = [
            {"slug": av["slug"], "label": av["label"], "sortOrder": i}
            for i, av in enumerate(values_by_attr.get(a["slug"], []))
        ]

    products = mysql_json_rows(f"""
        SELECT JSON_OBJECT(
          'wooId', ID, 'slug', post_name, 'name', post_title, 'status', post_status,
          'shortDescription', NULLIF(post_excerpt, ''), 'description', NULLIF(post_content, ''))
        FROM {t('posts')} WHERE post_type = 'product' AND post_status = 'publish'""")

    variations = mysql_json_rows(f"""
        SELECT JSON_OBJECT(
          'wooId', v.ID, 'parentWooId', v.post_parent, 'position', v.menu_order,
          'attrs', (SELECT JSON_OBJECTAGG(SUBSTRING(pm.meta_key, 14), pm.meta_value)
                    FROM {t('postmeta')} pm
                    WHERE pm.post_id = v.ID AND pm.meta_key LIKE 'attribute\\_pa\\_%'
                      AND pm.meta_value <> ''))
        FROM {t('posts')} v
        JOIN {t('posts')} p ON p.ID = v.post_parent AND p.post_status = 'publish'
        WHERE v.post_type = 'product_variation' AND v.post_status = 'publish'""")

    meta_rows = mysql_json_rows(f"""
        SELECT JSON_OBJECT('postId', pm.post_id, 'k', pm.meta_key, 'v', pm.meta_value)
        FROM {t('postmeta')} pm JOIN {t('posts')} p ON p.ID = pm.post_id
        WHERE p.post_type IN ('product', 'product_variation')
          AND pm.meta_key IN ({', '.join("'" + k + "'" for k in META_KEYS)})""")

    types = mysql_json_rows(f"""
        SELECT JSON_OBJECT('postId', tr.object_id, 'type', tm.name)
        FROM {t('term_relationships')} tr
        JOIN {t('term_taxonomy')} tt ON tt.term_taxonomy_id = tr.term_taxonomy_id
          AND tt.taxonomy = 'product_type'
        JOIN {t('terms')} tm ON tm.term_id = tt.term_id""")

    product_cats = mysql_json_rows(f"""
        SELECT JSON_OBJECT('postId', tr.object_id, 'termId', tt.term_id)
        FROM {t('term_relationships')} tr
        JOIN {t('term_taxonomy')} tt ON tt.term_taxonomy_id = tr.term_taxonomy_id
          AND tt.taxonomy = 'product_cat'""")

    product_tags = mysql_json_rows(f"""
        SELECT JSON_OBJECT('postId', tr.object_id, 'termId', tt.term_id)
        FROM {t('term_relationships')} tr
        JOIN {t('term_taxonomy')} tt ON tt.term_taxonomy_id = tr.term_taxonomy_id
          AND tt.taxonomy = 'product_tag'""")

    attachments = mysql_json_rows(f"""
        SELECT JSON_OBJECT(
          'id', a.ID,
          'file', (SELECT pm.meta_value FROM {t('postmeta')} pm
                   WHERE pm.post_id = a.ID AND pm.meta_key = '_wp_attached_file' LIMIT 1),
          'alt',  (SELECT pm.meta_value FROM {t('postmeta')} pm
                   WHERE pm.post_id = a.ID AND pm.meta_key = '_wp_attachment_image_alt' LIMIT 1))
        FROM {t('posts')} a WHERE a.post_type = 'attachment'""")

    meta = {}
    for row in meta_rows:
        meta.setdefault(row["postId"], {})[row["k"]] = row["v"]
    type_by_id = {r["postId"]: r["type"] for r in types}
    cats_by_id = {}
    for r in product_cats:
        cats_by_id.setdefault(r["postId"], []).append(r["termId"])
    tags_by_id = {}
    for r in product_tags:
        tags_by_id.setdefault(r["postId"], []).append(r["termId"])
    attachment_by_id = {a["id"]: a for a in attachments}
    variations_by_parent = {}
    for v in variations:
        variations_by_parent.setdefault(v["parentWooId"], []).append(v)

    def image_entries(pid, m):
        """Featured thumbnail first, then the gallery, deduplicated."""
        ids, seen = [], set()
        thumb = to_int(m.get("_thumbnail_id"))
        if thumb:
            ids.append((thumb, True))
            seen.add(thumb)
        for raw in (m.get("_product_image_gallery") or "").split(","):
            gid = to_int(raw.strip()) if raw.strip() else None
            if gid and gid not in seen:
                ids.append((gid, False))
                seen.add(gid)
        images = []
        for pos, (aid, featured) in enumerate(ids):
            att = attachment_by_id.get(aid)
            if not att or not att.get("file"):
                continue
            images.append({
                "attachmentId": aid,
                # interim key keeps the original wp-content path so the binary
                # migration (storage task) can locate and re-key the file
                "storageKey": "wp/" + att["file"],
                "alt": nonempty(att.get("alt")),
                "position": pos,
                "featured": featured,
            })
        return images

    out_products = []
    skipped_no_slug = 0
    for p in products:
        pid = p["wooId"]
        m = meta.get(pid, {})
        if not p.get("slug"):
            skipped_no_slug += 1
            continue
        ptype = type_by_id.get(pid, "simple")
        source_variants = []
        attribute_slugs = []
        if ptype == "variable":
            for v in sorted(variations_by_parent.get(pid, []), key=lambda x: (x["position"], x["wooId"])):
                vm = meta.get(v["wooId"], {})
                attrs = v.get("attrs") or {}
                for slug in attrs:
                    if slug not in attribute_slugs:
                        attribute_slugs.append(slug)
                source_variants.append({
                    "wooId": v["wooId"],
                    "sku": nonempty(vm.get("_sku")),
                    "regularPriceHuf": to_minor_huf(vm.get("_regular_price") or vm.get("_price")),
                    "salePriceHuf": to_minor_huf(vm.get("_sale_price")),
                    "saleFrom": unix_to_iso(vm.get("_sale_price_dates_from")),
                    "saleTo": unix_to_iso(vm.get("_sale_price_dates_to")),
                    "manageStock": vm.get("_manage_stock") == "yes",
                    "stockQty": to_int(vm.get("_stock")),
                    "weightGrams": to_grams(vm.get("_weight")),
                    "position": v["position"],
                    "attributes": attrs,
                })
        out_products.append({
            "wooId": pid,
            "slug": p["slug"],
            "name": p["name"],
            "type": ptype,
            "status": p["status"],
            "shortDescription": p.get("shortDescription"),
            "description": p.get("description"),
            "taxClass": nonempty(m.get("_tax_class")),
            "seoTitle": nonempty(m.get("_yoast_wpseo_title")),
            "metaDescription": nonempty(m.get("_yoast_wpseo_metadesc")),
            "categoryWooTermIds": cats_by_id.get(pid, []),
            "tagWooTermIds": tags_by_id.get(pid, []),
            "attributeSlugs": attribute_slugs,
            "sku": nonempty(m.get("_sku")),
            "regularPriceHuf": to_minor_huf(m.get("_regular_price") or m.get("_price")),
            "salePriceHuf": to_minor_huf(m.get("_sale_price")),
            "saleFrom": unix_to_iso(m.get("_sale_price_dates_from")),
            "saleTo": unix_to_iso(m.get("_sale_price_dates_to")),
            "manageStock": m.get("_manage_stock") == "yes",
            "stockQty": to_int(m.get("_stock")),
            "weightGrams": to_grams(m.get("_weight")),
            "variants": source_variants,
            "images": image_entries(pid, m),
        })

    catalog = {"categories": categories, "tags": tags, "attributes": attributes, "products": out_products}
    json.dump(catalog, sys.stdout, ensure_ascii=False, indent=1)

    variant_count = sum(len(p["variants"]) for p in out_products)
    image_count = sum(len(p["images"]) for p in out_products)
    print(f"\nexported: {len(out_products)} products, {variant_count} variations, "
          f"{len(categories)} categories, {len(attributes)} attributes, {image_count} images"
          + (f", skipped(no slug): {skipped_no_slug}" if skipped_no_slug else ""),
          file=sys.stderr)


if __name__ == "__main__":
    main()
