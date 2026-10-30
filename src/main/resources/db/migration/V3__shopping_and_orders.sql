-- Phase 5: wishlist, cart, multi-seller orders, delivery simulation, payments

CREATE TABLE wishlist_items (
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id  BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, product_id)
);

CREATE TABLE cart_items (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id  BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity    INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 20),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, product_id)
);

-- Customer-facing parent order. Address is a snapshot so later edits never rewrite history.
CREATE TABLE orders (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    status        VARCHAR(25) NOT NULL DEFAULT 'PLACED'
                  CHECK (status IN ('PLACED','IN_PROGRESS','DELIVERED','PARTIALLY_CANCELLED','CANCELLED')),
    ship_name     VARCHAR(120) NOT NULL,
    ship_phone    VARCHAR(30)  NOT NULL,
    ship_line1    VARCHAR(200) NOT NULL,
    ship_line2    VARCHAR(200),
    ship_city     VARCHAR(100) NOT NULL,
    ship_state    VARCHAR(100) NOT NULL,
    ship_postal   VARCHAR(20)  NOT NULL,
    ship_country  VARCHAR(60)  NOT NULL,
    subtotal      NUMERIC(12,2) NOT NULL,
    shipping_total NUMERIC(12,2) NOT NULL DEFAULT 0,
    total         NUMERIC(12,2) NOT NULL,
    is_demo       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_orders_user ON orders (user_id, created_at DESC);

-- One fulfilment group per seller within an order, each with its own status and delivery estimate.
CREATE TABLE seller_orders (
    id                 BIGSERIAL PRIMARY KEY,
    order_id           BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    seller_id          BIGINT NOT NULL REFERENCES sellers(id),
    status             VARCHAR(25) NOT NULL DEFAULT 'PLACED'
                       CHECK (status IN ('PLACED','CONFIRMED','PROCESSING','PACKED','SHIPPED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED')),
    estimated_delivery DATE NOT NULL,
    subtotal           NUMERIC(12,2) NOT NULL,
    cancel_reason      TEXT,
    cancelled_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_id, seller_id)
);
CREATE INDEX ix_seller_orders_seller ON seller_orders (seller_id, created_at DESC);

CREATE TABLE order_items (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    seller_order_id BIGINT NOT NULL REFERENCES seller_orders(id) ON DELETE CASCADE,
    product_id      BIGINT NOT NULL REFERENCES products(id),
    product_name    VARCHAR(500) NOT NULL,       -- snapshots: purchase-time values
    image_url       TEXT,
    unit_price      NUMERIC(12,2) NOT NULL,
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    status          VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CANCELLED')),
    cancel_reason   TEXT,
    cancelled_at    TIMESTAMPTZ,
    stock_restored  BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_order_items_order ON order_items (order_id);
CREATE INDEX ix_order_items_so ON order_items (seller_order_id);
CREATE INDEX ix_order_items_product ON order_items (product_id);

CREATE TABLE order_status_history (
    id              BIGSERIAL PRIMARY KEY,
    seller_order_id BIGINT NOT NULL REFERENCES seller_orders(id) ON DELETE CASCADE,
    status          VARCHAR(25) NOT NULL,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_history_so ON order_status_history (seller_order_id, created_at);

-- Payment state is separate from order state. No card data is ever stored.
CREATE TABLE payments (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    method       VARCHAR(20) NOT NULL CHECK (method IN ('COD')),
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID','FAILED','CANCELLED')),
    amount       NUMERIC(12,2) NOT NULL,
    provider_ref VARCHAR(100),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE products ADD COLUMN is_demo BOOLEAN NOT NULL DEFAULT FALSE;
