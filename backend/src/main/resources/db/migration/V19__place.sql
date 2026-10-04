-- Phase 2 place directory. Coordinates are provider-neutral so the UI can later use MapLibre.
CREATE TABLE place (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    name VARCHAR(180) NOT NULL,
    kind VARCHAR(24) NOT NULL DEFAULT 'CLINIC',
    address TEXT NOT NULL,
    area VARCHAR(120) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    phone VARCHAR(32),
    website_url TEXT,
    opening_hours JSONB NOT NULL DEFAULT '{}'::jsonb,
    specialties VARCHAR(120)[] NOT NULL DEFAULT '{}',
    badges VARCHAR(120)[] NOT NULL DEFAULT '{}',
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_place_kind CHECK (kind IN ('CLINIC', 'EMERGENCY', 'LAB', 'STORE')),
    CONSTRAINT ck_place_status CHECK (status IN ('PUBLISHED', 'HIDDEN')),
    CONSTRAINT ck_place_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_place_longitude CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_place_area_kind ON place (area, kind) WHERE status = 'PUBLISHED';
CREATE INDEX idx_place_coordinates ON place (latitude, longitude) WHERE status = 'PUBLISHED';

CREATE TABLE place_review (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    place_id UUID NOT NULL REFERENCES place(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL,
    body TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_place_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_place_review_status CHECK (status IN ('PUBLISHED', 'HIDDEN')),
    CONSTRAINT uq_place_review_user UNIQUE (place_id, user_id)
);

CREATE INDEX idx_place_review_place ON place_review (place_id, created_at DESC)
    WHERE status = 'PUBLISHED';

CREATE TRIGGER trg_place_updated_at BEFORE UPDATE ON place
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_place_review_updated_at BEFORE UPDATE ON place_review
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE place_booking (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    place_id UUID NOT NULL REFERENCES place(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    service_code VARCHAR(48) NOT NULL,
    booking_date DATE NOT NULL,
    time_slot VARCHAR(16) NOT NULL,
    note TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_place_booking_status CHECK (status IN ('REQUESTED', 'CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT uq_place_booking_slot UNIQUE (place_id, booking_date, time_slot)
);

CREATE TRIGGER trg_place_booking_updated_at BEFORE UPDATE ON place_booking
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO place (name, kind, address, area, latitude, longitude, phone, specialties, badges)
VALUES
    ('PetCare Center Thảo Điền', 'CLINIC', '124A Xuân Thủy, Thảo Điền, TP. Thủ Đức', 'Thảo Điền', 10.802420, 106.734120, '1900 8899', ARRAY['Theo dõi pH', 'Siêu âm'], ARRAY['Cat-friendly']),
    ('Samyang Anipol', 'CLINIC', '35 Song Hành, An Phú, TP. Thủ Đức', 'An Phú', 10.802870, 106.755110, '1900 6886', ARRAY['Nội soi', 'Theo dõi pH'], ARRAY['Phòng chờ riêng']),
    ('PetPro 24/7', 'EMERGENCY', '68 Nguyễn Văn Hưởng, Thảo Điền, TP. Thủ Đức', 'Thảo Điền', 10.807300, 106.732620, '1900 7799', ARRAY['Cấp cứu', 'Theo dõi ngoài giờ'], ARRAY['24/7'])
ON CONFLICT DO NOTHING;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_app') THEN
        GRANT SELECT, INSERT, UPDATE ON place, place_review, place_booking TO catcheck_app;
    END IF;
END $$;
