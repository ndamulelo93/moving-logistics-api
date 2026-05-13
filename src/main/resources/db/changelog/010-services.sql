--liquibase formatted sql

--changeset transport-booking:010-services
--comment: Create services table and service_vehicle_types join table for the service catalogue

CREATE TABLE services (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(200)    NOT NULL UNIQUE,
    description     TEXT,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE service_vehicle_types (
    service_id      BIGINT  NOT NULL REFERENCES services(id),
    vehicle_type_id BIGINT  NOT NULL REFERENCES vehicle_types(id),
    PRIMARY KEY (service_id, vehicle_type_id)
);

--rollback DROP TABLE IF EXISTS service_vehicle_types;
--rollback DROP TABLE IF EXISTS services;
