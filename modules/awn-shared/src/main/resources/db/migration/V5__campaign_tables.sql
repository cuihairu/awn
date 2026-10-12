-- =============================================================================
-- V5__campaign_tables.sql — Phase 02：专场（活动聚合页）
-- =============================================================================
-- 设计依据：docs/architecture/campaign-rendering.md、docs/adr/0005-campaign-revision-publish.md
-- 专场通过"商品引用"组织交易商品，自身不拥有库存和订单实现。
-- =============================================================================

CREATE TABLE campaigns (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_code   VARCHAR(64) NOT NULL UNIQUE,
    title           VARCHAR(128) NOT NULL,
    subtitle        VARCHAR(256),
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT'
                    CHECK (status IN ('DRAFT', 'VALIDATED', 'PUBLISHED', 'UNPUBLISHED', 'ARCHIVED')),
    current_revision_id BIGINT,
    config_snapshot JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE campaign_revisions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id     BIGINT NOT NULL REFERENCES campaigns (id),
    revision_number INT NOT NULL,
    config_snapshot JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (campaign_id, revision_number)
);

CREATE INDEX ix_campaign_revisions_campaign ON campaign_revisions (campaign_id);

-- 外键在 campaigns 表创建后添加（循环引用）
ALTER TABLE campaigns
    ADD CONSTRAINT fk_campaigns_current_revision
    FOREIGN KEY (current_revision_id) REFERENCES campaign_revisions (id);

CREATE TABLE campaign_events (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id     BIGINT NOT NULL REFERENCES campaigns (id),
    event_id        BIGINT NOT NULL REFERENCES event (id),
    display_order   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (campaign_id, event_id)
);

CREATE INDEX ix_campaign_events_campaign ON campaign_events (campaign_id);

CREATE TABLE campaign_products (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id     BIGINT NOT NULL REFERENCES campaigns (id),
    product_id      BIGINT NOT NULL REFERENCES product (id),
    display_order   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (campaign_id, product_id)
);

CREATE INDEX ix_campaign_products_campaign ON campaign_products (campaign_id);

CREATE TABLE campaign_assets (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id     BIGINT NOT NULL REFERENCES campaigns (id),
    asset_type      VARCHAR(32) NOT NULL
                    CHECK (asset_type IN ('IMAGE', 'VIDEO', 'BANNER', 'THUMBNAIL')),
    url             VARCHAR(512) NOT NULL,
    alt_text        VARCHAR(256),
    display_order   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_campaign_assets_campaign ON campaign_assets (campaign_id);

CREATE TRIGGER trg_campaign_updated_at BEFORE UPDATE ON campaigns
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_campaign_events_updated_at BEFORE UPDATE ON campaign_events
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_campaign_products_updated_at BEFORE UPDATE ON campaign_products
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
CREATE TRIGGER trg_campaign_assets_updated_at BEFORE UPDATE ON campaign_assets
    FOR EACH ROW EXECUTE FUNCTION awn_set_updated_at();
