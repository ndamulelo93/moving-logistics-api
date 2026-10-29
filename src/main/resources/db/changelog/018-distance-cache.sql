--liquibase formatted sql

--changeset transport-booking:018-distance-cache
--comment: Create distance_cache table for Google Maps API response caching

CREATE TABLE distance_cache (
    id              BIGSERIAL       PRIMARY KEY,
    pickup_address  VARCHAR(500)    NOT NULL,
    dropoff_address VARCHAR(500)    NOT NULL,
    distance_km     NUMERIC(10, 2)  NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    accessed_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT distance_cache_unique UNIQUE (pickup_address, dropoff_address),
    CONSTRAINT distance_km_positive CHECK (distance_km >= 0)
);

CREATE INDEX idx_distance_cache_addresses ON distance_cache (pickup_address, dropoff_address);
CREATE INDEX idx_distance_cache_accessed  ON distance_cache (accessed_at);

--rollback DROP INDEX IF EXISTS idx_distance_cache_accessed;
--rollback DROP INDEX IF EXISTS idx_distance_cache_addresses;
--rollback DROP TABLE IF EXISTS distance_cache;
