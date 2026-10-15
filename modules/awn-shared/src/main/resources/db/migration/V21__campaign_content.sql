-- 专场内容（须知 / 常见问题），docs/api/contracts.md「专场列表与内容接口」
-- 区块配置走 campaign 的配置快照（不可变 revision），须知与常见问题是运营逐条录入的独立内容，
-- 供引用型 props（ruleDocumentId / faqId）按条目 id 解析，不再悬空。
-- 素材沿用 V5 的 campaign_assets，本迁移只补展示顺序所需的一致索引。
CREATE TABLE campaign_rule_documents (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id     BIGINT NOT NULL REFERENCES campaigns (id),
    title           VARCHAR(128) NOT NULL,
    content         TEXT NOT NULL,
    display_order   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_campaign_rules_campaign
    ON campaign_rule_documents (campaign_id, display_order, id);

CREATE TABLE campaign_faqs (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id     BIGINT NOT NULL REFERENCES campaigns (id),
    question        VARCHAR(256) NOT NULL,
    answer          TEXT NOT NULL,
    display_order   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_campaign_faqs_campaign ON campaign_faqs (campaign_id, display_order, id);

-- 公开读按素材排序取用，与须知 / 常见问题同口径
CREATE INDEX ix_campaign_assets_ordered ON campaign_assets (campaign_id, display_order, id);

-- 内容行只有录入与删除（无更新端点），不挂 updated_at 触发器