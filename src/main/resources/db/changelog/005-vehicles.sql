--liquibase formatted sql

--changeset transport-booking:005-vehicles
--comment: Create vehicles table for the fleet of transport vehicles

CREATE TABLE vehicles (
    id                  BIGSERIAL       PRIMARY KEY,
    registration_number VARCHAR(20)     NOT NULL UNIQUE,
    make                VARCHAR(100)    NOT NULL,
    model               VARCHAR(100)    NOT NULL,
    year                SMALLINT        NOT NULL,
    vehicle_type_id     BIGINT          NOT NULL REFERENCES vehicle_types(id),
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vehicles_vehicle_type_id ON vehicles(vehicle_type_id);
CREATE INDEX idx_vehicles_active          ON vehicles(active);

--rollback DROP INDEX IF EXISTS idx_vehicles_active;
--rollback DROP INDEX IF EXISTS idx_vehicles_vehicle_type_id;
--rollback DROP TABLE IF EXISTS vehicles;
