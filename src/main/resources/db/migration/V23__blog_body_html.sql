-- V23: blog body migrates from Markdown to sanitized HTML (ADR 0009).
-- body_markdown is retained (deprecated) for one release, then dropped.
ALTER TABLE blog_post ADD COLUMN body_html TEXT NOT NULL DEFAULT '';
