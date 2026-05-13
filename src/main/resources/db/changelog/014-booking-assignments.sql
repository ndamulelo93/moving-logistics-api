--liquibase formatted sql

--changeset transport-booking:014-booking-assignments
--comment: Create booking_assignments table linking bookings to assigned drivers and vehicles

CREATE TABLE booking_assignments (
    id          BIGSERIAL       PRIMARY KEY,
    booking_id  BIGINT          NOT NULL UNIQUE REFERENCES bookings(id),
    driver_id   BIGINT          NOT NULL REFERENCES drivers(id),
    vehicle_id  BIGINT          NOT NULL REFERENCES vehicles(id),
    assigned_at TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    assigned_by BIGINT          NOT NULL REFERENCES admin_users(id)
);

CREATE INDEX idx_booking_assignments_driver_id  ON booking_assignments(driver_id);
CREATE INDEX idx_booking_assignments_vehicle_id ON booking_assignments(vehicle_id);

--rollback DROP INDEX IF EXISTS idx_booking_assignments_vehicle_id;
--rollback DROP INDEX IF EXISTS idx_booking_assignments_driver_id;
--rollback DROP TABLE IF EXISTS booking_assignments;
