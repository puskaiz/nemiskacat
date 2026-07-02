-- Slug uniqueness for product tags (matches blog_tag; admin now creates tags by slug).
ALTER TABLE product_tag ADD CONSTRAINT product_tag_slug_key UNIQUE (slug);
