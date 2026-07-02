import importlib.util
import json
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "export", Path(__file__).parent / "export.py")
export = importlib.util.module_from_spec(spec)
spec.loader.exec_module(export)

PRODUCT_WOO_ID = 101


def test_catalog_emits_product_tag_terms_and_per_product_tag_ids(monkeypatch, capsys):
    monkeypatch.setattr(export, "mysql_scalar", lambda q: None, raising=False)

    def fake_rows(q):
        # product_tag terms (no term_relationships join)
        if "taxonomy = 'product_tag'" in q and "term_relationships" not in q:
            return [{"wooTermId": 42, "slug": "vintage", "name": "Vintage"}]
        # product_tag relationships (has term_relationships join)
        if "taxonomy = 'product_tag'" in q:
            return [{"postId": PRODUCT_WOO_ID, "termId": 42}]
        # product_cat terms
        if "taxonomy = 'product_cat'" in q and "term_relationships" not in q:
            return []
        # product_cat relationships
        if "taxonomy = 'product_cat'" in q:
            return []
        # attribute taxonomies
        if "woocommerce_attribute_taxonomies" in q:
            return []
        # pa_* attribute values
        if "LIKE 'pa\\\\_%'" in q or "LIKE 'pa\\_%" in q:
            return []
        # published products
        if "post_type = 'product'" in q:
            return [{"wooId": PRODUCT_WOO_ID, "slug": "vintage-mug",
                     "name": "Vintage Mug", "status": "publish",
                     "shortDescription": None, "description": None}]
        # variations
        if "product_variation" in q:
            return []
        # postmeta (meta keys)
        if "postmeta" in q and "meta_key" in q:
            return []
        # product_type taxonomy
        if "taxonomy = 'product_type'" in q:
            return []
        # attachments
        if "post_type = 'attachment'" in q:
            return []
        return []

    monkeypatch.setattr(export, "mysql_json_rows", fake_rows)
    export.main()
    out = capsys.readouterr().out
    data = json.loads(out)

    assert "tags" in data
    assert data["tags"][0]["slug"] == "vintage"
    assert data["products"][0]["tagWooTermIds"] == [42]
