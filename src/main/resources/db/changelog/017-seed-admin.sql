--liquibase formatted sql

--changeset transport-booking:017-seed-admin
--comment: Seed initial SUPER_ADMIN user for local development (password: Admin@1234)

INSERT INTO admin_users (username, display_name, password_hash, role, active)
VALUES (
    'admin',
    'System Administrator',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCMc9bud48Pex20La2oHDOS',
    'SUPER_ADMIN',
    TRUE
);

--rollback DELETE FROM admin_users WHERE username = 'admin';
