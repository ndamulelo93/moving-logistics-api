--liquibase formatted sql

--changeset transport-booking:011-bookings
--comment: Create bookings table — the central entity of the transport booking system

CREATE TABLE bookings (
    id                  BIGSERIAL           PRIMARY KEY,
    booking_reference   VARCHAR(12)         NOT NULL UNIQUE,
    status              booking_status      NOT NULL DEFAULT 'PENDING',
    customer_name       VARCHAR(200)        NOT NULL,
    customer_phone      VARCHAR(20)         NOT NULL,
    customer_email      VARCHAR(320)        NOT NULL,
    vehicle_type_id     BIGINT              NOT NULL REFERENCES vehicle_types(id),
    service_date        DATE                NOT NULL,
    service_time        TIME,
    helpers             SMALLINT            NOT NULL DEFAULT 0,
    stairs              BOOLEAN             NOT NULL DEFAULT FALSE,
    estimated_price     NUMERIC(10,2),
    final_price         NUMERIC(10,2),
    total_paid          NUMERIC(10,2)       NOT NULL DEFAULT 0,
    admin_notes         TEXT,
    created_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bookings_booking_reference ON bookings(booking_reference);
CREATE INDEX idx_bookings_status            ON bookings(status);
CREATE INDEX idx_bookings_service_date      ON bookings(service_date);
CREATE INDEX idx_bookings_vehicle_type_id   ON bookings(vehicle_type_id);
CREATE INDEX idx_bookings_customer_name     ON bookings(LOWER(customer_name));
CREATE INDEX idx_bookings_created_at        ON bookings(created_at);

--rollback DROP INDEX IF EXISTS idx_bookings_created_at;
--rollback DROP INDEX IF EXISTS idx_bookings_customer_name;
--rollback DROP INDEX IF EXISTS idx_bookings_vehicle_type_id;
--rollback DROP INDEX IF EXISTS idx_bookings_service_date;
--rollback DROP INDEX IF EXISTS idx_bookings_status;
--rollback DROP INDEX IF EXISTS idx_bookings_booking_reference;
--rollback DROP TABLE IF EXISTS bookings;
