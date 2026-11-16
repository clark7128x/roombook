-- V2: Database schema according to Stage 3 (03-data.md)

-- 1. Table room
CREATE TABLE room (
    id BIGSERIAL CONSTRAINT pk_room PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    name_key VARCHAR(200) NOT NULL,
    floor INTEGER NOT NULL,
    capacity INTEGER NOT NULL,
    equipment VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_room_name_key UNIQUE (name_key),
    CONSTRAINT ck_room_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_room_capacity_positive CHECK (capacity >= 1)
);

-- 2. Table app_user
CREATE TABLE app_user (
    id BIGSERIAL CONSTRAINT pk_app_user PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    username_key VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_app_user_username_key UNIQUE (username_key),
    CONSTRAINT ck_app_user_role CHECK (role IN ('EMPLOYEE', 'ADMIN'))
);

-- 3. Table booking
CREATE TABLE booking (
    id BIGSERIAL CONSTRAINT pk_booking PRIMARY KEY,
    room_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    cancelled_by_id BIGINT,
    CONSTRAINT fk_booking_room FOREIGN KEY (room_id) REFERENCES room(id),
    CONSTRAINT fk_booking_author FOREIGN KEY (author_id) REFERENCES app_user(id),
    CONSTRAINT fk_booking_cancelled_by FOREIGN KEY (cancelled_by_id) REFERENCES app_user(id),
    CONSTRAINT ex_booking_no_overlap EXCLUDE USING gist (
        room_id WITH =,
        tsrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status = 'ACTIVE'),
    CONSTRAINT ck_booking_interval CHECK (start_at < end_at AND CAST(start_at AS date) = CAST(end_at AS date)),
    CONSTRAINT ck_booking_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_booking_cancel_state CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by_id IS NOT NULL) OR
        (status <> 'CANCELLED' AND cancelled_at IS NULL AND cancelled_by_id IS NULL)
    ),
    CONSTRAINT ck_booking_title_not_blank CHECK (btrim(title) <> '')
);

-- 4. Indexes for booking
CREATE INDEX ix_booking_start ON booking (start_at);
CREATE INDEX ix_booking_room_start ON booking (room_id, start_at DESC, id DESC);
CREATE INDEX ix_booking_author_start ON booking (author_id, start_at DESC, id DESC);
