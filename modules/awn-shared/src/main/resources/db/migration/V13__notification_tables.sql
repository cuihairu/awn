-- V13__notification_tables.sql — notification 模块：通知消息（站内信等通道投递留痕）
CREATE TABLE notification_messages (
    id            BIGSERIAL PRIMARY KEY,
    recipient_id  BIGINT       NOT NULL,
    channel       VARCHAR(32)  NOT NULL,
    event_type    VARCHAR(64)  NOT NULL,
    title         VARCHAR(200) NOT NULL,
    body          TEXT         NOT NULL,
    reference     VARCHAR(128),
    status        VARCHAR(32)  NOT NULL,
    fail_reason   TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_status CHECK (status IN ('SENT', 'FAILED'))
);
CREATE INDEX idx_notification_recipient ON notification_messages (recipient_id, id DESC);
