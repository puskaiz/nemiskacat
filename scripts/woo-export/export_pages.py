#!/usr/bin/env python3
"""Export selected WordPress content pages (Elementor) to a SourcePages JSON snapshot.

Walks each page's `_elementor_data` widget tree in document order and emits, per page:
externalId (wp page id), slug, title, assembled bodyHtml, status, publishedAt (UTC),
seoTitle, seoDescription. Images are NOT downloaded here — the Java PageImporter fetches
and re-keys them. Driven by an editable SLUGS list so it can be re-run on an updated set.

Usage:
    python3 scripts/woo-export/export_pages.py > /tmp/pages.json
Then import (from the app):
    mvn spring-boot:run -Dspring-boot.run.profiles=local,import-pages \
      -Dspring-boot.run.arguments=--webshop.import.pages-file=/tmp/pages.json
Re-running the import is safe: upsert by wp page id (externalId), slug never changes.
"""
import html
import json
import re
import subprocess
import sys

CONTAINER = "wp_db"
DB = "client7002dbnem"
PREFIX = "guxdop_"

# Editable list of page slugs to export (re-runnable before go-live).
SLUGS = [
    "butorfestes-kezdoknek", "rolunk", "latvanymuhely", "kapcsolat", "csapatunk",
    "itt-hallottal-meg-rolunk", "butorfestes-otletek", "gyakran-ismetelt-kerdesek",
    "altalanos-szerzodesi-feltetelek", "adatvedelem",
]


def t(name):
    return PREFIX + name


def query(sql):
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "mysql", "-uroot", "-proot_password",
         "--default-character-set=utf8mb4", "-B", "-N", "--raw", DB, "-e", sql],
        capture_output=True, text=True, check=True)
    return result.stdout


def plain(htmltext):
    return html.unescape(re.sub(r"<[^>]+>", " ", htmltext or "")).strip()


def to_iso_utc(date_gmt):
    """WP post_date_gmt 'YYYY-MM-DD HH:MM:SS' (UTC) -> ISO-8601 '...Z'; None for the zero date."""
    if not date_gmt or date_gmt.startswith("0000-00-00"):
        return None
    return date_gmt.replace(" ", "T") + "Z"


def walk(node, title, parts):
    """Append HTML section fragments to `parts` in document order. Mirrors export-workshops.py."""
    if isinstance(node, dict):
        wt = node.get("widgetType")
        s = node.get("settings", {}) or {}
        if wt == "heading":
            text = plain(s.get("title", ""))
            if text and text.lower() != title.strip().lower():
                parts.append(f"<h2>{html.escape(text)}</h2>")
        elif wt == "text-editor":
            editor = (s.get("editor") or "").strip()
            if editor:
                parts.append(editor)
        elif wt in ("toggle", "accordion"):
            for tab in (s.get("tabs") or []):
                q = plain(tab.get("tab_title", ""))
                a = (tab.get("tab_content") or "").strip()
                if q:
                    parts.append(f"<h3>{html.escape(q)}</h3>")
                if a:
                    parts.append(a)
        elif wt == "image":
            img = s.get("image", {}) or {}
            url = img.get("url")
            if url:
                alt = html.escape(plain(s.get("alt", "")) or "")
                parts.append(f'<img src="{html.escape(url)}" alt="{alt}">')
        elif wt in ("media-carousel", "image-carousel"):
            for sl in (s.get("slides") or s.get("carousel") or []):
                if not isinstance(sl, dict):
                    continue
                img = sl.get("image", {}) if isinstance(sl.get("image"), dict) else {}
                url = img.get("url") or sl.get("url")
                if url:
                    parts.append(f'<img src="{html.escape(url)}" alt="">')
        HANDLED_WIDGETS = {"heading", "text-editor", "toggle", "accordion",
                           "image", "media-carousel", "image-carousel"}
        if wt in HANDLED_WIDGETS:
            return  # leaf widget fully handled; don't re-walk its own settings
        for v in node.values():
            walk(v, title, parts)
    elif isinstance(node, list):
        for v in node:
            walk(v, title, parts)


def export_page(row):
    pid = row["id"]
    raw = query(
        f"SELECT meta_value FROM {t('postmeta')} "
        f"WHERE post_id={pid} AND meta_key='_elementor_data';").strip()
    parts = []
    if raw:
        walk(json.loads(raw), row["title"], parts)
    body = "\n".join(parts).strip() or (row.get("content") or "")
    return {
        "externalId": pid,
        "slug": row["slug"],
        "title": row["title"],
        "bodyHtml": body,
        "status": row["status"],
        "publishedAt": to_iso_utc(row.get("date_gmt")),
        "seoTitle": row.get("seo_title") or None,
        "seoDescription": row.get("seo_desc") or None,
    }


def main():
    slug_list = ",".join("'" + s.replace("'", "") + "'" for s in SLUGS)
    rows = [json.loads(line) for line in query(f"""
        SELECT JSON_OBJECT(
          'id', p.ID, 'slug', p.post_name, 'title', p.post_title,
          'content', p.post_content, 'status', p.post_status, 'date_gmt', p.post_date_gmt,
          'seo_title', (SELECT pm.meta_value FROM {t('postmeta')} pm
                        WHERE pm.post_id=p.ID AND pm.meta_key='_yoast_wpseo_title' LIMIT 1),
          'seo_desc', (SELECT pm.meta_value FROM {t('postmeta')} pm
                       WHERE pm.post_id=p.ID AND pm.meta_key='_yoast_wpseo_metadesc' LIMIT 1))
        FROM {t('posts')} p
        WHERE p.post_type='page' AND p.post_name IN ({slug_list})""").splitlines()
            if line.strip()]
    pages = [export_page(r) for r in rows]
    json.dump({"pages": pages}, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
