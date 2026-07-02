#!/usr/bin/env python3
"""Export workshops from the legacy WooCommerce/Elementor DB to JSON.

The 5 workshops are WordPress Pages built with Elementor; their content + image slider live in
`lxoplw_postmeta._elementor_data`. This script walks each page's Elementor widget tree in document
order and emits, per workshop: externalId (wp page id), slug, name, a single assembled
`descriptionHtml` (the titled content sections), and `images` (the media-carousel slider, ordered).

Dated ticket-products / sessions are intentionally NOT exported — sessions are created in the new
app's admin.

Usage:
    python3 scripts/woo-export/export-workshops.py > workshops.json

Then import (from the app):
    mvn spring-boot:run -Dspring-boot.run.profiles=local,import-workshops \
      -Dspring-boot.run.arguments=--webshop.import.workshops-file=workshops.json

Requires the local MariaDB dump container to be running (see scripts/woo-export/README.md).
"""
import html
import json
import re
import subprocess
import sys

# Local MariaDB container holding the loaded `client7002dbwork` dump.
CONTAINER = "nemiskacat-work-db"
DB = "client7002dbwork"

# The 5 workshop landing pages (post_type='page'), by wp page id.
PAGE_IDS = [38573, 40301, 43799, 43532, 42806]

# Body headings to drop: the dynamic sessions block and the trailing newsletter CTA.
SKIP_HEADING_EXACT = {"következő időpontok"}
SKIP_HEADING_PREFIX = ("érdekelnek a workshopok",)
SKIP_HEADING_CONTAINS = ("kérj értesítést",)


def query(sql):
    """Run a read-only SQL query. --raw is required so the Elementor JSON is not mangled."""
    result = subprocess.run(
        ["docker", "exec", CONTAINER, "mariadb", "-uroot", "-proot_password",
         "--default-character-set=utf8mb4", "-N", "--raw", DB, "-e", sql],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        sys.exit(f"query failed: {result.stderr.strip()}")
    return result.stdout


def page_row(pid):
    out = query(
        f"SELECT post_name, post_title FROM lxoplw_posts WHERE ID={pid};"
    ).strip("\n")
    slug, _, title = out.partition("\t")
    return slug.strip(), title.strip()


def elementor_data(pid):
    raw = query(
        f"SELECT meta_value FROM lxoplw_postmeta "
        f"WHERE post_id={pid} AND meta_key='_elementor_data';"
    ).strip()
    return json.loads(raw) if raw else []


def heading_skipped(text, title):
    t = text.strip().lower()
    if not t:
        return True
    if t == title.strip().lower():  # don't repeat the page H1 in the body
        return True
    if t in SKIP_HEADING_EXACT:
        return True
    if any(t.startswith(p) for p in SKIP_HEADING_PREFIX):
        return True
    if any(c in t for c in SKIP_HEADING_CONTAINS):
        return True
    return False


def plain(htmltext):
    return html.unescape(re.sub(r"<[^>]+>", " ", htmltext or "")).strip()


def walk(node, title, parts, images):
    """Append HTML section fragments to `parts` and slider URLs to `images`, in document order."""
    if isinstance(node, dict):
        wt = node.get("widgetType")
        s = node.get("settings", {}) or {}
        if wt == "heading":
            text = plain(s.get("title", ""))
            if not heading_skipped(text, title):
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
        elif wt in ("media-carousel", "image-carousel"):
            for sl in (s.get("slides") or s.get("carousel") or []):
                if not isinstance(sl, dict):
                    continue
                img = sl.get("image", {}) if isinstance(sl.get("image"), dict) else {}
                url = img.get("url") or sl.get("url")
                if url:
                    images.append(url)
        elif wt == "image":
            img = s.get("image", {}) or {}
            url = img.get("url")
            if url:
                alt = html.escape(plain(s.get("alt", "")) or "")
                parts.append(f'<img src="{html.escape(url)}" alt="{alt}">')
        for v in node.values():
            walk(v, title, parts, images)
    elif isinstance(node, list):
        for v in node:
            walk(v, title, parts, images)


def export_workshop(pid):
    slug, title = page_row(pid)
    data = elementor_data(pid)
    parts, image_urls = [], []
    walk(data, title, parts, image_urls)
    images = [{"url": u, "position": i} for i, u in enumerate(image_urls)]
    return {
        "externalId": pid,
        "slug": slug,
        "name": title,
        "descriptionHtml": "\n".join(parts).strip(),
        "images": images,
    }


def main():
    workshops = [export_workshop(pid) for pid in PAGE_IDS]
    json.dump({"workshops": workshops}, sys.stdout, ensure_ascii=False, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
