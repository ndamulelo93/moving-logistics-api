--liquibase formatted sql

--changeset transport-booking:004-vehicle-types
--comment: Create vehicle_types table for categorising transport vehicles

CREATE TABLE vehicle_types (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL UNIQUE,
    description     TEXT,
    load_capacity   VARCHAR(100),
    base_fee        NUMERIC(10,2)   NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

--rollback DROP TABLE IF EXISTS vehicle_types;
