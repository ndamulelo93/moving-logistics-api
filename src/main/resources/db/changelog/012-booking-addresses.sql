--liquibase formatted sql

--changeset transport-booking:012-booking-addresses
--comment: Create booking_addresses table for pickup and dropoff address details

CREATE TABLE booking_addresses (
    id              BIGSERIAL       PRIMARY KEY,
    booking_id      BIGINT          NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    address_type    address_type    NOT NULL,
    address_line    VARCHAR(500)    NOT NULL,
    suburb          VARCHAR(200),
    city            VARCHAR(200),
    sort_order      SMALLINT        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_booking_addresses_booking_id ON booking_addresses(booking_id);

--rollback DROP INDEX IF EXISTS idx_booking_addresses_booking_id;
--rollback DROP TABLE IF EXISTS booking_addresses;
