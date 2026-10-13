-- V15__identity_tables.sql — identity 模块：用户与角色
CREATE TABLE identity_users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(100),
    status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_identity_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE identity_user_roles (
    user_id    BIGINT      NOT NULL REFERENCES identity_users (id),
    role       VARCHAR(64) NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role)
);
