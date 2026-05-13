--liquibase formatted sql

--changeset transport-booking:006-drivers
--comment: Create drivers table for transport drivers

CREATE TABLE drivers (
    id              BIGSERIAL       PRIMARY KEY,
    full_name       VARCHAR(200)    NOT NULL,
    phone           VARCHAR(20)     NOT NULL UNIQUE,
    licence_number  VARCHAR(50)     NOT NULL UNIQUE,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_drivers_active ON drivers(active);

--rollback DROP INDEX IF EXISTS idx_drivers_active;
--rollback DROP TABLE IF EXISTS drivers;
