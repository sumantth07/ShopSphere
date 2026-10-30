-- Idempotent: safe to re-run. Whole import is one transaction, so a failure leaves the catalog untouched.
BEGIN;

-- 1. Categories
INSERT INTO categories (name, slug)
SELECT DISTINCT category,
       trim(both '-' from regexp_replace(lower(category), '[^a-z0-9]+', '-', 'g'))
FROM stg_products WHERE category <> ''
ON CONFLICT (name) DO NOTHING;

-- 2. Imported sellers: catalog attribution only. user_id stays NULL and status SUSPENDED-equivalent
--    (PENDING) so nobody can log in as, or manage, these stores.
INSERT INTO sellers (store_name, origin, application_status)
SELECT DISTINCT seller, 'IMPORTED', 'PENDING' FROM stg_products WHERE seller <> ''
ON CONFLICT (lower(store_name)) WHERE origin = 'IMPORTED' DO NOTHING;

-- 3. Products. source_id is preserved; re-running updates source-owned fields but never touches stock edits.
INSERT INTO products (source_id, name, description, price, category_id, brand, image_url, seller_id,
                      imported_seller_name, listing_available, stock_quantity, stock_source,
                      rating, review_count, created_at)
SELECT s.id::bigint, s.name, NULLIF(s.description,''), s.price::numeric(12,2), c.id, NULLIF(s.brand,''), s.image,
       sl.id, NULLIF(s.seller,''), s.available::boolean,
       -- DEMO STOCK (the CSV has no stock column): unavailable -> 0; otherwise deterministic 3..100.
       CASE WHEN s.available::boolean THEN 3 + (s.id::bigint % 98) ELSE 0 END,
       'DEMO_SEEDED',
       NULLIF(s.rating,'')::numeric(2,1), NULLIF(s.review_count,'')::int, s.created_at::timestamptz
FROM stg_products s
JOIN categories c ON c.name = s.category
LEFT JOIN sellers sl ON lower(sl.store_name) = lower(s.seller) AND sl.origin = 'IMPORTED'
ON CONFLICT (source_id) DO UPDATE SET
    name = EXCLUDED.name, description = EXCLUDED.description, price = EXCLUDED.price,
    category_id = EXCLUDED.category_id, brand = EXCLUDED.brand, image_url = EXCLUDED.image_url,
    listing_available = EXCLUDED.listing_available, rating = EXCLUDED.rating,
    review_count = EXCLUDED.review_count, updated_at = now();

-- 4. Additional images (JSON array -> rows)
INSERT INTO product_images (product_id, url, position)
SELECT p.id, j.url, (j.ord - 1)::int
FROM stg_products s
JOIN products p ON p.source_id = s.id::bigint
CROSS JOIN LATERAL jsonb_array_elements_text(s.more_images::jsonb) WITH ORDINALITY AS j(url, ord)
ON CONFLICT (product_id, url) DO NOTHING;

COMMIT;

-- 5. Verification
SELECT (SELECT count(*) FROM stg_products) AS staged,
       (SELECT count(*) FROM products WHERE source_id IS NOT NULL) AS imported,
       (SELECT count(*) FROM categories) AS categories,
       (SELECT count(*) FROM sellers WHERE origin='IMPORTED') AS imported_sellers,
       (SELECT count(*) FROM product_images) AS images;
