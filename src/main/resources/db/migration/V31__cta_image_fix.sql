-- Correct the CTA image: V30 pointed at the wrong paint-swatch banner; the intended
-- image is the Svenska Blue chalk paint tin. Non-destructive jsonb merge of imageUrl only.
UPDATE sidebar_block
SET content = (content::jsonb || jsonb_build_object('imageUrl', '/design/assets/blog/cta-paint.jpg'))::text
WHERE block_type = 'CTA';
