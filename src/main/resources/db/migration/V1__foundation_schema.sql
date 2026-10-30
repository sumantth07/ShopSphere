-- ShopSphere V1: accounts foundation + catalog. Later phases add carts, orders, payments, etc.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users (
    id                BIGSERIAL PRIMARY KEY,
    full_name         VARCHAR(120) NOT NULL,
    email             VARCHAR(254) NOT NULL,           -- stored lower-cased/trimmed by the application
    email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    role              VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER' CHECK (role IN ('CUSTOMER','ADMIN')),
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'   CHECK (status IN ('ACTIVE','SUSPENDED','DELETED')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (email);

CREATE TABLE sellers (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       REFERENCES users(id),   -- NULL for imported catalog sellers with no ShopSphere account
    store_name        VARCHAR(120) NOT NULL,
    store_description TEXT,
    logo_url          TEXT,
    contact_email     VARCHAR(254),
    origin            VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED' CHECK (origin IN ('REGISTERED','IMPORTED')),
    application_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'    CHECK (application_status IN ('PENDING','APPROVED','REJECTED','SUSPENDED')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_seller_user CHECK (origin = 'IMPORTED' OR user_id IS NOT NULL)
);
CREATE UNIQUE INDEX ux_sellers_user ON sellers (user_id) WHERE user_id IS NOT NULL;   -- one store per user
CREATE UNIQUE INDEX ux_sellers_imported_name ON sellers (lower(store_name)) WHERE origin = 'IMPORTED';

CREATE TABLE categories (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(100) NOT NULL UNIQUE,
    slug  VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE products (
    id                    BIGSERIAL PRIMARY KEY,
    source_id             BIGINT UNIQUE,                    -- id column of products_rows.csv (NULL for marketplace-created products)
    name                  VARCHAR(500) NOT NULL,
    description           TEXT,
    price                 NUMERIC(12,2) NOT NULL CHECK (price > 0),
    category_id           BIGINT NOT NULL REFERENCES categories(id),
    brand                 VARCHAR(200),
    image_url             TEXT NOT NULL,
    seller_id             BIGINT REFERENCES sellers(id),
    imported_seller_name  VARCHAR(200),                     -- original CSV attribution, kept as metadata
    listing_available     BOOLEAN NOT NULL DEFAULT TRUE,    -- CSV "available" flag (listing availability, not inventory)
    stock_quantity        INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    stock_source          VARCHAR(20) NOT NULL DEFAULT 'SELLER' CHECK (stock_source IN ('SELLER','DEMO_SEEDED')),
    rating                NUMERIC(2,1) CHECK (rating BETWEEN 0 AND 5),
    review_count          INTEGER CHECK (review_count >= 0),
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    version               BIGINT NOT NULL DEFAULT 0,        -- optimistic locking for concurrent stock updates
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_products_category   ON products (category_id);
CREATE INDEX ix_products_brand      ON products (lower(brand));
CREATE INDEX ix_products_price      ON products (price);
CREATE INDEX ix_products_rating     ON products (rating DESC NULLS LAST, review_count DESC NULLS LAST);
CREATE INDEX ix_products_created    ON products (created_at DESC, id DESC);
CREATE INDEX ix_products_seller     ON products (seller_id);
CREATE INDEX ix_products_name_trgm  ON products USING gin (lower(name) gin_trgm_ops);
CREATE INDEX ix_products_brand_trgm ON products USING gin (lower(brand) gin_trgm_ops);

CREATE TABLE product_images (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    url         TEXT   NOT NULL,
    position    INTEGER NOT NULL DEFAULT 0,
    UNIQUE (product_id, url)
);
CREATE INDEX ix_product_images_product ON product_images (product_id, position);
