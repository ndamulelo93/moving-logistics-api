--liquibase formatted sql

--changeset transport-booking:007-pricing-rules
--comment: Create pricing_rules table for vehicle-type-based pricing configuration

CREATE TABLE pricing_rules (
    id                      BIGSERIAL       PRIMARY KEY,
    vehicle_type_id         BIGINT          NOT NULL REFERENCES vehicle_types(id),
    base_fee                NUMERIC(10,2)   NOT NULL,
    per_km_fee              NUMERIC(10,2)   NOT NULL,
    helper_fee              NUMERIC(10,2)   NOT NULL,
    stairs_surcharge        NUMERIC(10,2)   NOT NULL DEFAULT 0,
    same_day_surcharge      NUMERIC(10,2)   NOT NULL DEFAULT 0,
    after_hours_surcharge   NUMERIC(10,2)   NOT NULL DEFAULT 0,
    effective_from          DATE            NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_pricing_rules_vt_effective
    ON pricing_rules(vehicle_type_id, effective_from);
CREATE INDEX idx_pricing_rules_vehicle_type_id ON pricing_rules(vehicle_type_id);

--rollback DROP INDEX IF EXISTS idx_pricing_rules_vehicle_type_id;
--rollback DROP INDEX IF EXISTS idx_pricing_rules_vt_effective;
--rollback DROP TABLE IF EXISTS pricing_rules;
