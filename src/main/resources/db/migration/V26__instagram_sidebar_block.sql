-- T-IG-BLOCK: add the INSTAGRAM sidebar block (display_order=6, enabled=true).
-- Renders nothing until instagram.enabled=true and posts are synced — safe to ship.
INSERT INTO sidebar_block (block_type, display_order, enabled, content) VALUES
 ('INSTAGRAM', 6, true,
  '{"title":"Kövess minket Instagramon","count":6}');
