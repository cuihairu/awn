-- =============================================================================
-- V25__order_attendees.sql — 观演人实名（一票一证，todo 148 余项）
-- =============================================================================
-- 设计依据：docs/design/prototypes/web/purchase.html 步骤 3（数量与实名，原型过目
-- 已代决 2026-10-14）：观演人姓名 + 证件号逐票绑定，提交后不可修改，展示时脱敏。
-- 每张票一条实名记录（数量型行 quantity>1 时按提交顺序逐张对应）；
-- 同一场次同一证件至多一张（跨订单唯一，唯一索引兜底；session_id 为 NULL 的行
-- 不参与该约束——未声明场次的行无从按场次判重）。
-- =============================================================================

CREATE TABLE order_attendees (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id    BIGINT NOT NULL REFERENCES order_items (id),
    session_id VARCHAR(64),
    name       VARCHAR(64) NOT NULL,
    id_no      VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_order_attendees_item ON order_attendees (item_id);

-- 实名一票一证：同一场次同证件至多一条（session_id 为 NULL 时 PG 唯一索引按不同值处理）
CREATE UNIQUE INDEX ux_order_attendees_session_idno
    ON order_attendees (session_id, id_no);
