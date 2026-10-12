-- =============================================================================
-- V2__inventory_tables.sql — Phase 03 第一片：库存池、余额、预留、流水
-- =============================================================================
-- 设计依据：docs/architecture/inventory-consistency.md
-- CHECK 约束是最后一道防线（reserved + sold <= total 且计数非负）；
-- 第一道防线是应用层原子条件 UPDATE（见 InventoryBalanceRepository）。
-- =============================================================================

CREATE TABLE inventory_pool (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pool_code  VARCHAR(64) NOT NULL UNIQUE,
    product_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 同一商品在同一场次只允许一个库存池
CREATE UNIQUE INDEX ux_inventory_pool_product_session ON inventory_pool (product_id, session_id);

CREATE TABLE inventory_balance (
    inventory_pool_id BIGINT PRIMARY KEY REFERENCES inventory_pool (id),
    total_quantity    INT NOT NULL CHECK (total_quantity >= 0),
    reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    sold_quantity     INT NOT NULL DEFAULT 0 CHECK (sold_quantity >= 0),
    version           BIGINT NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (reserved_quantity + sold_quantity <= total_quantity)
);

CREATE TABLE inventory_reservation (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_no    VARCHAR(64) NOT NULL UNIQUE,
    inventory_pool_id BIGINT NOT NULL REFERENCES inventory_pool (id),
    order_ref         VARCHAR(64),
    quantity          INT NOT NULL CHECK (quantity > 0),
    status            VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'CONFIRMED', 'RELEASED')),
    expires_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_inventory_reservation_pool  ON inventory_reservation (inventory_pool_id);
CREATE INDEX ix_inventory_reservation_order ON inventory_reservation (order_ref);

-- 库存流水：RESERVE / CONFIRM / RELEASE / ADJUST
CREATE TABLE inventory_transaction (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inventory_pool_id BIGINT NOT NULL REFERENCES inventory_pool (id),
    reservation_id    BIGINT REFERENCES inventory_reservation (id),
    type              VARCHAR(16) NOT NULL CHECK (type IN ('RESERVE', 'CONFIRM', 'RELEASE', 'ADJUST')),
    quantity          INT NOT NULL CHECK (quantity > 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_inventory_transaction_pool ON inventory_transaction (inventory_pool_id, created_at);

CREATE TRIGGER trg_inventory_pool_updated_at BEFORE UPDATE ON inventory_pool
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_inventory_balance_updated_at BEFORE UPDATE ON inventory_balance
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_inventory_reservation_updated_at BEFORE UPDATE ON inventory_reservation
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
