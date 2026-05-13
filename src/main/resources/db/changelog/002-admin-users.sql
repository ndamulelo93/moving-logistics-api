--liquibase formatted sql

--changeset transport-booking:002-admin-users
--comment: Create admin_users table for system administrators

CREATE TABLE admin_users (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(100)    NOT NULL UNIQUE,
    display_name    VARCHAR(200)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    role            admin_role      NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_users_username ON admin_users(username);

--rollback DROP INDEX IF EXISTS idx_admin_users_username;
--rollback DROP TABLE IF EXISTS admin_users;
