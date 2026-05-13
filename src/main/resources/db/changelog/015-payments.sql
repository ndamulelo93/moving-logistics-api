--liquibase formatted sql

--changeset transport-booking:015-payments
--comment: Create payments table for capturing payment records against bookings

CREATE TABLE payments (
    id               BIGSERIAL       PRIMARY KEY,
    booking_id       BIGINT          NOT NULL REFERENCES bookings(id),
    amount           NUMERIC(10,2)   NOT NULL,
    payment_method   payment_method  NOT NULL,
    reference_number VARCHAR(100),
    captured_by      BIGINT          NOT NULL REFERENCES admin_users(id),
    captured_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_booking_id ON payments(booking_id);

--rollback DROP INDEX IF EXISTS idx_payments_booking_id;
--rollback DROP TABLE IF EXISTS payments;
