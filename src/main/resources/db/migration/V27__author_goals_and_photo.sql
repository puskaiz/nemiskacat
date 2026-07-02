-- Author sidebar block: add the "goals" text (mirrored from the live nemiskacat.hu
-- sidebar) and point the photo at the transferred, app-hosted asset.
-- jsonb merge (||) sets only photoUrl + goals, preserving any admin-edited name/bio.
UPDATE sidebar_block
SET content = (content::jsonb || jsonb_build_object(
        'photoUrl', '/design/assets/blog/author-eniko.jpg',
        'goals', 'Eltökélt szándékom a lelkes bútorfestők támogatása, bátorítása. Legyen szó modern vagy rusztikus felületről, teljesen kezdő vagy már gyakorlottabb haladóról, mindenki számára tartogatok új technikákat.'
    ))::text
WHERE block_type = 'AUTHOR';
