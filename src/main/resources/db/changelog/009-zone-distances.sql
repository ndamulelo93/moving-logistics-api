--liquibase formatted sql

--changeset transport-booking:009-zone-distances
--comment: Create zone_distances table for admin-managed inter-zone distance lookup

CREATE TABLE zone_distances (
    id              BIGSERIAL       PRIMARY KEY,
    zone_from       VARCHAR(50)     NOT NULL,
    zone_to         VARCHAR(50)     NOT NULL,
    distance_km     NUMERIC(8,2)    NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_zone_pair UNIQUE (zone_from, zone_to)
);

CREATE INDEX idx_zone_distances_pair ON zone_distances(zone_from, zone_to);

--rollback DROP INDEX IF EXISTS idx_zone_distances_pair;
--rollback DROP TABLE IF EXISTS zone_distances;
