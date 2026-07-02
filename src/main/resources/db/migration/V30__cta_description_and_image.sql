-- CTA (Ajánló) sidebar block: add the description text (mirrored from the live
-- nemiskacat.hu sidebar) and point the image at the transferred, app-hosted asset.
-- jsonb merge (||) sets only imageUrl + description, preserving title/buttonLabel/url.
UPDATE sidebar_block
SET content = (content::jsonb || jsonb_build_object(
        'imageUrl', '/design/assets/blog/cta-paint.png',
        'description', 'Nézz körbe a Nemiskacat webshopjában, válogass a színek között és szerezz be minden bútorfestéshez szükséges kelléket.'
    ))::text
WHERE block_type = 'CTA';
