--liquibase formatted sql

--changeset transport-booking:008-suburb-zones
--comment: Create suburb_zones table mapping suburbs to delivery zones

CREATE TABLE suburb_zones (
    id          BIGSERIAL       PRIMARY KEY,
    suburb_name VARCHAR(200)    NOT NULL,
    zone_name   VARCHAR(50)     NOT NULL,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_suburb_zones_suburb_name ON suburb_zones(LOWER(suburb_name));
CREATE INDEX idx_suburb_zones_zone_name          ON suburb_zones(zone_name);

--rollback DROP INDEX IF EXISTS idx_suburb_zones_zone_name;
--rollback DROP INDEX IF EXISTS idx_suburb_zones_suburb_name;
--rollback DROP TABLE IF EXISTS suburb_zones;
