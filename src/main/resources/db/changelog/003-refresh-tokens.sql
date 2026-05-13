--liquibase formatted sql

--changeset transport-booking:003-refresh-tokens
--comment: Create refresh_tokens table for JWT refresh token rotation

CREATE TABLE refresh_tokens (
    id              BIGSERIAL       PRIMARY KEY,
    token           VARCHAR(36)     NOT NULL UNIQUE,
    family_id       VARCHAR(36)     NOT NULL,
    admin_user_id   BIGINT          NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
    used            BOOLEAN         NOT NULL DEFAULT FALSE,
    expires_at      TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token     ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens(admin_user_id);

--rollback DROP INDEX IF EXISTS idx_refresh_tokens_user_id;
--rollback DROP INDEX IF EXISTS idx_refresh_tokens_family_id;
--rollback DROP INDEX IF EXISTS idx_refresh_tokens_token;
--rollback DROP TABLE IF EXISTS refresh_tokens;
