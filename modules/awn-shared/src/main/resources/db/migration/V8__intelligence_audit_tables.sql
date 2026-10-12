-- =============================================================================
-- V8__intelligence_audit_tables.sql — Phase 06：情报 schema、审计、告警
-- =============================================================================
-- 设计依据：docs/architecture/intelligence-isolation.md（情报与交易隔离，
--           数据分类不允许混用）、docs/architecture/domain-model.md（情报表组）。
-- 情报表位于 intelligence schema：awn_intel 角色只拥有该 schema 权限，
-- 对交易表零权限（deploy/config/postgres/init-intel.sql 审计）。
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS intelligence;

-- 情报来源：数据分类与可信度（docs/architecture/intelligence-isolation.md）
CREATE TABLE intelligence.data_sources (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_no   VARCHAR(64) NOT NULL UNIQUE,
    name        VARCHAR(128) NOT NULL,
    kind        VARCHAR(32) NOT NULL CHECK (kind IN (
                    'OFFICIAL_PUBLISHED', 'THIRD_PARTY_LISTED',
                    'HISTORICAL_COLLECTED', 'EXTERNAL_AVAILABILITY')),
    trust_tier  SMALLINT    NOT NULL CHECK (trust_tier IN (1, 2, 3)),
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 原始观察：采集即落库，标准化失败也可溯源重放
CREATE TABLE intelligence.raw_observations (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id               BIGINT      NOT NULL REFERENCES intelligence.data_sources (id),
    source_event_ref        VARCHAR(128) NOT NULL,
    source_product_ref      VARCHAR(128) NOT NULL,
    observed_at             TIMESTAMPTZ NOT NULL,
    price                   BIGINT,
    currency                VARCHAR(3),
    availability_label      VARCHAR(32),
    raw_payload             JSONB       NOT NULL,
    normalization_version   VARCHAR(16) NOT NULL,
    quality_status          VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (quality_status IN (
                                'PENDING', 'OK', 'REJECTED')),
    reject_reason           VARCHAR(256),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_id, source_event_ref)
);
CREATE INDEX ix_raw_observations_source ON intelligence.raw_observations (source_id, observed_at);

-- 标准化观察：通过质量检查的观察才进入标准化结果
CREATE TABLE intelligence.normalized_observations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    raw_observation_id BIGINT   NOT NULL UNIQUE REFERENCES intelligence.raw_observations (id),
    source_id       BIGINT      NOT NULL REFERENCES intelligence.data_sources (id),
    product_code    VARCHAR(64) NOT NULL,
    session_code    VARCHAR(64),
    price           BIGINT      NOT NULL CHECK (price > 0),
    currency        VARCHAR(3)  NOT NULL,
    availability    VARCHAR(32),
    observed_at     TIMESTAMPTZ NOT NULL,
    trust_tier      SMALLINT    NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_normalized_observations_product ON intelligence.normalized_observations (product_code, observed_at);

-- 价格历史：每条价格可溯源（来源、更新时间、可信度）
CREATE TABLE intelligence.price_history (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id       BIGINT      NOT NULL REFERENCES intelligence.data_sources (id),
    product_code    VARCHAR(64) NOT NULL,
    session_code    VARCHAR(64),
    price           BIGINT      NOT NULL CHECK (price > 0),
    currency        VARCHAR(3)  NOT NULL,
    availability    VARCHAR(32),
    observed_at     TIMESTAMPTZ NOT NULL,
    trust_tier      SMALLINT    NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_price_history_product ON intelligence.price_history (product_code, session_code, observed_at);

-- 审计日志：敏感操作留痕（操作者、动作、对象、明细）
CREATE TABLE audit_logs (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor        VARCHAR(64)  NOT NULL,
    action       VARCHAR(64)  NOT NULL,
    target_type  VARCHAR(64)  NOT NULL,
    target_id    VARCHAR(128) NOT NULL,
    detail       JSONB,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_logs_target ON audit_logs (target_type, target_id, created_at);
CREATE INDEX ix_audit_logs_actor  ON audit_logs (actor, created_at);

-- 运营告警：库存异常 / 支付异常 / 对账差异三类可触发、可确认
CREATE TABLE alerts (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category     VARCHAR(32)  NOT NULL CHECK (category IN ('INVENTORY', 'PAYMENT', 'RECONCILIATION')),
    severity     VARCHAR(16)  NOT NULL CHECK (severity IN ('INFO', 'WARN', 'CRITICAL')),
    message      VARCHAR(512) NOT NULL,
    reference    VARCHAR(128),
    acknowledged BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_alerts_open ON alerts (acknowledged, category, created_at);

-- 库存调整流水允许负数（人工校正可增可减，方向记入流水）
ALTER TABLE inventory_transaction DROP CONSTRAINT inventory_transaction_quantity_check;
ALTER TABLE inventory_transaction ADD CONSTRAINT inventory_transaction_quantity_check CHECK (quantity <> 0);

CREATE TRIGGER trg_data_sources_updated_at BEFORE UPDATE ON intelligence.data_sources
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_raw_observations_updated_at BEFORE UPDATE ON intelligence.raw_observations
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
