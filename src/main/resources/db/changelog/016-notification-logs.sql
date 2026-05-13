--liquibase formatted sql

--changeset transport-booking:016-notification-logs
--comment: Create notification_logs table for tracking async notification dispatch and retries

CREATE TABLE notification_logs (
    id              BIGSERIAL               PRIMARY KEY,
    booking_id      BIGINT                  NOT NULL REFERENCES bookings(id),
    event_type      VARCHAR(50)             NOT NULL,
    channel         notification_channel,
    status          notification_status     NOT NULL DEFAULT 'PENDING',
    attempt_count   SMALLINT                NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    error_detail    TEXT,
    created_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ             NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_logs_booking_id ON notification_logs(booking_id);
CREATE INDEX idx_notification_logs_status     ON notification_logs(status);

--rollback DROP INDEX IF EXISTS idx_notification_logs_status;
--rollback DROP INDEX IF EXISTS idx_notification_logs_booking_id;
--rollback DROP TABLE IF EXISTS notification_logs;
