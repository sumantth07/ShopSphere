-- Run inside psql connected to the shopsphere database, from backend/db/import
-- (Flyway V1 must already have been applied by starting the backend once, or run the V1 file manually.)
DROP TABLE IF EXISTS stg_products;
CREATE TABLE stg_products (
    id TEXT, name TEXT, description TEXT, price TEXT, category TEXT, image TEXT, seller TEXT,
    created_at TEXT, available TEXT, brand TEXT, review_count TEXT, rating TEXT, more_images TEXT
);
-- Load the CLEANED csv (python3 scripts/clean_products_csv.py ...). \copy runs client-side, so no server file permissions are needed.
\copy stg_products FROM '../data/products_clean.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
SELECT count(*) AS staged_rows FROM stg_products;
