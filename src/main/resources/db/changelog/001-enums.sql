--liquibase formatted sql

--changeset transport-booking:001-enums
--comment: Create PostgreSQL enum types for the transport booking system

CREATE TYPE booking_status AS ENUM (
    'PENDING', 'QUOTED', 'CONFIRMED', 'ASSIGNED',
    'IN_PROGRESS', 'COMPLETED', 'CANCELLED'
);

CREATE TYPE admin_role AS ENUM (
    'SUPER_ADMIN', 'DISPATCHER', 'VIEW_ONLY'
);

CREATE TYPE address_type AS ENUM ('PICKUP', 'DROPOFF');

CREATE TYPE notification_channel AS ENUM ('WHATSAPP', 'EMAIL');

CREATE TYPE notification_status AS ENUM ('PENDING', 'SENT', 'FAILED');

CREATE TYPE payment_method AS ENUM ('CASH', 'EFT', 'CARD', 'OTHER');

CREATE TYPE history_entry_type AS ENUM ('STATUS_CHANGE', 'PRICE_ADJUSTMENT');

--rollback DROP TYPE IF EXISTS history_entry_type;
--rollback DROP TYPE IF EXISTS payment_method;
--rollback DROP TYPE IF EXISTS notification_status;
--rollback DROP TYPE IF EXISTS notification_channel;
--rollback DROP TYPE IF EXISTS address_type;
--rollback DROP TYPE IF EXISTS admin_role;
--rollback DROP TYPE IF EXISTS booking_status;
