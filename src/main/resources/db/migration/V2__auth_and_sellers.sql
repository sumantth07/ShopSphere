-- Phase 3/4: authentication, addresses, seller application fields
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);               -- NULL for Google-only accounts
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMPTZ;

-- External identities are keyed by (provider, subject), never by email.
CREATE TABLE user_identities (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider    VARCHAR(20) NOT NULL CHECK (provider IN ('GOOGLE')),
    subject     VARCHAR(255) NOT NULL,
    email       VARCHAR(254),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, subject),
    UNIQUE (user_id, provider)
);

-- One table for all emailed one-time codes. Only a hash of the code is stored.
CREATE TABLE one_time_codes (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose      VARCHAR(20) NOT NULL CHECK (purpose IN ('EMAIL_VERIFY','PASSWORD_RESET','GOOGLE_LINK')),
    code_hash    CHAR(64) NOT NULL,
    attempts     INTEGER NOT NULL DEFAULT 0,
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ,
    link_token_hash CHAR(64),            -- GOOGLE_LINK only
    ext_subject  VARCHAR(255),           -- GOOGLE_LINK only: verified Google "sub"
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_otp_user_purpose ON one_time_codes (user_id, purpose, created_at DESC);
CREATE UNIQUE INDEX ux_otp_link_token ON one_time_codes (link_token_hash) WHERE link_token_hash IS NOT NULL;

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  CHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_user ON refresh_tokens (user_id);

CREATE TABLE addresses (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label       VARCHAR(40),
    full_name   VARCHAR(120) NOT NULL,
    phone       VARCHAR(30)  NOT NULL,
    line1       VARCHAR(200) NOT NULL,
    line2       VARCHAR(200),
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20)  NOT NULL,
    country     VARCHAR(60)  NOT NULL DEFAULT 'India',
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_addresses_user ON addresses (user_id);
CREATE UNIQUE INDEX ux_address_default ON addresses (user_id) WHERE is_default;

ALTER TABLE sellers ADD COLUMN contact_phone VARCHAR(30);
ALTER TABLE sellers ADD COLUMN rejection_reason TEXT;
ALTER TABLE sellers ADD COLUMN reviewed_by BIGINT REFERENCES users(id);
ALTER TABLE sellers ADD COLUMN reviewed_at TIMESTAMPTZ;

CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(40) NOT NULL,
    message     TEXT NOT NULL,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);

CREATE TABLE audit_logs (
    id            BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id),
    action        VARCHAR(60) NOT NULL,
    target_type   VARCHAR(40),
    target_id     VARCHAR(40),
    detail        TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
