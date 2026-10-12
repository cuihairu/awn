-- =============================================================================
-- V4__catalog_tables.sql — Phase 02：活动、场次、票档、商品
-- =============================================================================
-- 设计依据：docs/architecture/domain-model.md
-- 库存池（inventory_pool）已在 V2 建立，本迁移建立活动与商品目录。
-- =============================================================================

CREATE TABLE venue (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_code VARCHAR(64) NOT NULL UNIQUE,
    name       VARCHAR(128) NOT NULL,
    city       VARCHAR(64),
    address    VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE event (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_code  VARCHAR(64) NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
                CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED')),
    start_time  TIMESTAMPTZ,
    end_time    TIMESTAMPTZ,
    venue_id    BIGINT REFERENCES venue (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_event_venue ON event (venue_id);

CREATE TABLE session (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_code VARCHAR(64) NOT NULL UNIQUE,
    event_id     BIGINT NOT NULL REFERENCES event (id),
    name         VARCHAR(128) NOT NULL,
    start_time   TIMESTAMPTZ NOT NULL,
    end_time     TIMESTAMPTZ,
    status       VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
                 CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_session_event ON session (event_id);

CREATE TABLE ticket_category (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_code VARCHAR(64) NOT NULL UNIQUE,
    name         VARCHAR(128) NOT NULL,
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE product (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_code VARCHAR(64) NOT NULL UNIQUE,
    event_id    BIGINT NOT NULL REFERENCES event (id),
    session_id  BIGINT REFERENCES session (id),
    category_id BIGINT NOT NULL REFERENCES ticket_category (id),
    name        VARCHAR(128) NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
                CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    unit_price  BIGINT NOT NULL CHECK (unit_price >= 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_product_event  ON product (event_id);
CREATE INDEX ix_product_session ON product (session_id);
CREATE INDEX ix_product_category ON product (category_id);

CREATE TRIGGER trg_venue_updated_at BEFORE UPDATE ON venue
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_event_updated_at BEFORE UPDATE ON event
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_session_updated_at BEFORE UPDATE ON session
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_ticket_category_updated_at BEFORE UPDATE ON ticket_category
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_product_updated_at BEFORE UPDATE ON product
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
