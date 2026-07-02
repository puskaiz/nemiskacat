import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    "export_blog", Path(__file__).parent / "export-blog.py")
export_blog = importlib.util.module_from_spec(spec)
spec.loader.exec_module(export_blog)


def test_assembles_post_with_cover_and_categories(monkeypatch, capsys):
    monkeypatch.setattr(export_blog, "mysql_scalar", lambda q: "https://nemiskacat.hu")

    def fake_rows(q):
        if "post_type = 'post'" in q:
            return [{"id": 7, "slug": "elso", "title": "Első", "excerpt": "",
                     "content": "<p>x</p>", "status": "publish",
                     "date_gmt": "2024-03-01 09:00:00", "thumb_id": "11",
                     "seo_title": None, "seo_desc": None}]
        if "post_type = 'attachment'" in q:
            return [{"id": 11, "file": "2024/01/cover.jpg"}]
        if "taxonomy = 'category'" in q and "term_relationships" not in q:
            return [{"name": "Hírek", "slug": "hirek"}]
        if "taxonomy = 'category'" in q:
            return [{"post_id": 7, "slug": "hirek"}]
        if "taxonomy = 'post_tag'" in q and "term_relationships" not in q:
            return [{"name": "Vintage", "slug": "vintage"}]
        if "taxonomy = 'post_tag'" in q:
            return [{"post_id": 7, "slug": "vintage"}]
        return []

    monkeypatch.setattr(export_blog, "mysql_json_rows", fake_rows)
    export_blog.main()
    out = capsys.readouterr().out
    import json
    data = json.loads(out)
    assert set(data.keys()) == {"posts", "categories", "tags"}
    post = data["posts"][0]
    assert post["externalId"] == 7 and post["slug"] == "elso" and post["status"] == "publish"
    assert post["publishedAt"] == "2024-03-01T09:00:00Z"
    assert post["coverImageUrl"] == "https://nemiskacat.hu/wp-content/uploads/2024/01/cover.jpg"
    assert post["categorySlugs"] == ["hirek"]
    assert data["categories"][0]["slug"] == "hirek"
    assert post["tagSlugs"] == ["vintage"]
    assert data["tags"][0]["slug"] == "vintage"
