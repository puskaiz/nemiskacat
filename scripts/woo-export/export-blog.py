#!/usr/bin/env python3
"""Export the WordPress blog (posts + categories) from the local wp_db container to JSON.

Produces a SourceBlog snapshot (see integrations/wordpress DTOs) consumed by the
BlogImporter via the `import-blog` profile. Reads ONLY blog tables. publish + draft.

Usage:
    python3 scripts/woo-export/export-blog.py > /tmp/blog.json
Then import (from the app):
    mvn spring-boot:run -Dspring-boot.run.profiles=local,import-blog \
      -Dspring-boot.run.arguments=--webshop.import.blog-file=/tmp/blog.json
"""
import json
import subprocess
import sys

CONTAINER = "wp_db"
DB = "client7002dbnem"
PREFIX = "guxdop_"


def mysql_json_rows(query):
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", "-proot_password",
         "--default-character-set=utf8mb4", "-B", "-N", "--raw", DB, "-e", query],
        capture_output=True, text=True, check=True)
    return [json.loads(line) for line in result.stdout.splitlines() if line.strip()]


def mysql_scalar(query):
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", "-proot_password",
         "--default-character-set=utf8mb4", "-B", "-N", "--raw", DB, "-e", query],
        capture_output=True, text=True, check=True)
    return result.stdout.strip()


def nonempty(v):
    return v if v not in (None, "") else None


def to_iso_utc(date_gmt):
    """WP post_date_gmt 'YYYY-MM-DD HH:MM:SS' (UTC) -> ISO-8601 '...Z'; null for the zero date."""
    if not date_gmt or date_gmt.startswith("0000-00-00"):
        return None
    return date_gmt.replace(" ", "T") + "Z"


def main():
    t = lambda name: PREFIX + name
    site_url = mysql_scalar(f"SELECT option_value FROM {t('options')} WHERE option_name='siteurl' LIMIT 1")
    uploads_base = site_url.rstrip("/") + "/wp-content/uploads/"

    posts = mysql_json_rows(f"""
        SELECT JSON_OBJECT(
          'id', p.ID, 'slug', p.post_name, 'title', p.post_title,
          'excerpt', p.post_excerpt, 'content', p.post_content,
          'status', p.post_status, 'date_gmt', p.post_date_gmt,
          'thumb_id', (SELECT pm.meta_value FROM {t('postmeta')} pm
                       WHERE pm.post_id = p.ID AND pm.meta_key = '_thumbnail_id' LIMIT 1),
          'seo_title', (SELECT pm.meta_value FROM {t('postmeta')} pm
                        WHERE pm.post_id = p.ID AND pm.meta_key = '_yoast_wpseo_title' LIMIT 1),
          'seo_desc', (SELECT pm.meta_value FROM {t('postmeta')} pm
                       WHERE pm.post_id = p.ID AND pm.meta_key = '_yoast_wpseo_metadesc' LIMIT 1))
        FROM {t('posts')} p
        WHERE p.post_type = 'post' AND p.post_status IN ('publish','draft')""")

    # featured-image relative file path per attachment id
    files = mysql_json_rows(f"""
        SELECT JSON_OBJECT('id', a.ID, 'file',
          (SELECT pm.meta_value FROM {t('postmeta')} pm
           WHERE pm.post_id = a.ID AND pm.meta_key = '_wp_attached_file' LIMIT 1))
        FROM {t('posts')} a WHERE a.post_type = 'attachment'""")
    file_by_id = {f["id"]: f.get("file") for f in files}

    categories = mysql_json_rows(f"""
        SELECT JSON_OBJECT('name', t.name, 'slug', t.slug)
        FROM {t('terms')} t
        JOIN {t('term_taxonomy')} tt ON tt.term_id = t.term_id
        WHERE tt.taxonomy = 'category'""")

    rels = mysql_json_rows(f"""
        SELECT JSON_OBJECT('post_id', tr.object_id, 'slug', t.slug)
        FROM {t('term_relationships')} tr
        JOIN {t('term_taxonomy')} tt ON tt.term_taxonomy_id = tr.term_taxonomy_id
        JOIN {t('terms')} t ON t.term_id = tt.term_id
        WHERE tt.taxonomy = 'category'""")
    cats_by_post = {}
    for r in rels:
        cats_by_post.setdefault(r["post_id"], []).append(r["slug"])

    tags = mysql_json_rows(f"""
        SELECT JSON_OBJECT('name', t.name, 'slug', t.slug)
        FROM {t('terms')} t
        JOIN {t('term_taxonomy')} tt ON tt.term_id = t.term_id
        WHERE tt.taxonomy = 'post_tag'""")

    tag_rels = mysql_json_rows(f"""
        SELECT JSON_OBJECT('post_id', tr.object_id, 'slug', t.slug)
        FROM {t('term_relationships')} tr
        JOIN {t('term_taxonomy')} tt ON tt.term_taxonomy_id = tr.term_taxonomy_id
        JOIN {t('terms')} t ON t.term_id = tt.term_id
        WHERE tt.taxonomy = 'post_tag'""")
    tags_by_post = {}
    for r in tag_rels:
        tags_by_post.setdefault(r["post_id"], []).append(r["slug"])

    out_posts = []
    for p in posts:
        thumb_id = p.get("thumb_id")
        cover = None
        if thumb_id and int(thumb_id) in file_by_id and file_by_id[int(thumb_id)]:
            cover = uploads_base + file_by_id[int(thumb_id)]
        out_posts.append({
            "externalId": p["id"],
            "slug": p["slug"],
            "title": p["title"],
            "excerpt": nonempty(p.get("excerpt")),
            "contentHtml": p.get("content") or "",
            "status": p["status"],
            "publishedAt": to_iso_utc(p.get("date_gmt")),
            "coverImageUrl": cover,
            "seoTitle": nonempty(p.get("seo_title")),
            "seoDescription": nonempty(p.get("seo_desc")),
            "categorySlugs": cats_by_post.get(p["id"], []),
            "tagSlugs": tags_by_post.get(p["id"], []),
        })

    json.dump({"posts": out_posts, "categories": categories, "tags": tags}, sys.stdout, ensure_ascii=False, indent=1)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
