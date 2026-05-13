--liquibase formatted sql

--changeset transport-booking:013-status-history
--comment: Create status_history table for immutable audit trail of booking status changes and price adjustments

CREATE TABLE status_history (
    id              BIGSERIAL           PRIMARY KEY,
    booking_id      BIGINT              NOT NULL REFERENCES bookings(id),
    entry_type      history_entry_type  NOT NULL DEFAULT 'STATUS_CHANGE',
    previous_status booking_status,
    new_status      booking_status,
    old_price       NUMERIC(10,2),
    new_price       NUMERIC(10,2),
    actor_type      VARCHAR(10)         NOT NULL,
    actor_admin_id  BIGINT              REFERENCES admin_users(id),
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_status_history_booking_id  ON status_history(booking_id);
CREATE INDEX idx_status_history_created_at  ON status_history(created_at);

--rollback DROP INDEX IF EXISTS idx_status_history_created_at;
--rollback DROP INDEX IF EXISTS idx_status_history_booking_id;
--rollback DROP TABLE IF EXISTS status_history;
