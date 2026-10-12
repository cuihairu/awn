-- 修正情报表可信度字段类型：实体使用 int（INTEGER），V8 误用 SMALLINT
ALTER TABLE intelligence.data_sources ALTER COLUMN trust_tier TYPE integer;
ALTER TABLE intelligence.normalized_observations ALTER COLUMN trust_tier TYPE integer;
ALTER TABLE intelligence.price_history ALTER COLUMN trust_tier TYPE integer;
