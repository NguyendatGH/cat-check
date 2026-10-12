-- Replace the initial HCMC directory seed with the supplied Da Nang directory.
-- Existing bookings keep their place_id: booked rows are re-pointed to the first
-- Da Nang entries, while unbooked rows can be safely removed.

CREATE TEMP TABLE _danang_places (
    seed_no INTEGER PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    kind VARCHAR(24) NOT NULL,
    address TEXT NOT NULL,
    area VARCHAR(120) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(9, 6) NOT NULL,
    phone VARCHAR(32),
    specialties VARCHAR(120)[] NOT NULL,
    badges VARCHAR(120)[] NOT NULL
) ON COMMIT DROP;

INSERT INTO _danang_places (seed_no, name, kind, address, area, latitude, longitude, phone, specialties, badges)
VALUES
    (1, 'Siêu thị thú cưng Pet Mart', 'STORE', '151 Nguyễn Văn Linh, Hải Châu, Đà Nẵng', 'Hải Châu', 16.060500, 108.213600, '19002100', ARRAY['Pet shop'], ARRAY['Đà Nẵng']),
    (2, 'Phòng khám thú y Danang Veterinary Clinic', 'CLINIC', '54 Nguyễn Phẩm, Hoà Cường Bắc, Hải Châu, Đà Nẵng', 'Hải Châu', 16.036000, 108.214000, '090 529 53 36', ARRAY['Khám thú y'], ARRAY['Đà Nẵng']),
    (3, 'Phòng khám bác sĩ thú y Trường Sơn', 'CLINIC', '152/1 Lý Tự Trọng, Hải Châu, Đà Nẵng', 'Hải Châu', 16.073000, 108.216000, '0236 3838 030', ARRAY['Khám thú y'], ARRAY['Đà Nẵng']),
    (4, 'Trung tâm Thú y vùng', 'CLINIC', '12 Trần Quý Cáp, Thạch Thang, Hải Châu, Đà Nẵng', 'Hải Châu', 16.071000, 108.218000, '0236 3889 362', ARRAY['Khám thú y'], ARRAY['Đà Nẵng']),
    (5, 'Petshop thú cưng Pet Mart', 'STORE', '319 Điện Biên Phủ, Thanh Khê, Đà Nẵng', 'Thanh Khê', 16.066000, 108.188000, '19002100', ARRAY['Pet shop'], ARRAY['Đà Nẵng']),
    (6, 'Phòng khám thú y Trường Chinh', 'CLINIC', '249 Trường Chinh, An Khê, Thanh Khê, Đà Nẵng', 'Thanh Khê', 16.039000, 108.184000, '097 610 01 77', ARRAY['Khám thú y'], ARRAY['Đà Nẵng']),
    (7, 'Bệnh viện thú y Tân Tiến Phát', 'EMERGENCY', '243 Trường Chinh, An Khê, Thanh Khê, Đà Nẵng', 'Thanh Khê', 16.039500, 108.184500, '090 500 69 96', ARRAY['Cấp cứu', 'Khám thú y'], ARRAY['Đà Nẵng']),
    (8, 'Cửa hàng thuốc thú y Thành Yến', 'STORE', '44 Nguyễn Viết Xuân, Hòa Minh, Liên Chiểu, Đà Nẵng', 'Liên Chiểu', 16.071000, 108.153000, '0236 3769 172', ARRAY['Thuốc thú y'], ARRAY['Đà Nẵng']),
    (9, 'Phòng khám thú y Thanh Sơn', 'CLINIC', '131 Hồ Tùng Mậu, Liên Chiểu, Đà Nẵng', 'Liên Chiểu', 16.075000, 108.151000, '093 246 80 11', ARRAY['Khám thú y'], ARRAY['Đà Nẵng']),
    (10, 'Dịch vụ thú y Phó Đức Chính', 'CLINIC', 'K85/16 Phó Đức Chính, Mân Thái, Sơn Trà, Đà Nẵng', 'Sơn Trà', 16.084000, 108.244000, '090 511 81 69', ARRAY['Khám thú y'], ARRAY['Đà Nẵng']),
    (11, 'Trạm Thú y CMT8', 'CLINIC', '371 Cách Mạng Tháng Tám, Hòa Thọ Đông, Cẩm Lệ, Đà Nẵng', 'Cẩm Lệ', 16.018000, 108.194000, '0236 3846 165', ARRAY['Khám thú y'], ARRAY['Đà Nẵng']),
    (12, 'Trạm thú y Lê Văn Hiến', 'CLINIC', '570 Lê Văn Hiến, Ngũ Hành Sơn, Đà Nẵng', 'Ngũ Hành Sơn', 16.007000, 108.260000, '0236 3961 127', ARRAY['Khám thú y'], ARRAY['Đà Nẵng']),
    (13, 'Bệnh viện thú y Lê Quang Đạo', 'EMERGENCY', 'Nhà D16, K38 đường Lê Quang Đạo, Ngũ Hành Sơn, Đà Nẵng', 'Ngũ Hành Sơn', 16.033000, 108.249000, '096 399 9383', ARRAY['Cấp cứu', 'Khám thú y'], ARRAY['Đà Nẵng']),
    (14, 'Danangpet Animal Hospital', 'EMERGENCY', 'Nhà D16 – Khu gia đình QĐ K38 / Lô 9 – B2.20 Khu đô thị sinh thái Nam Hòa Xuân, Ngũ Hành Sơn, Đà Nẵng', 'Ngũ Hành Sơn', 16.013000, 108.257000, '0963.999.383', ARRAY['Cấp cứu', 'Khám thú y'], ARRAY['Đà Nẵng']);

-- Keep rows that are referenced by bookings, but remove stale unbooked seed rows.
DELETE FROM place p
WHERE NOT EXISTS (SELECT 1 FROM place_booking b WHERE b.place_id = p.id);

-- Preserve booked place IDs while replacing their displayed directory data.
WITH existing AS (
    SELECT id, row_number() OVER (ORDER BY created_at, id) AS seed_no
    FROM place
), mapped AS (
    SELECT e.id, s.*
    FROM existing e
    JOIN _danang_places s ON s.seed_no = e.seed_no
)
UPDATE place p
SET name = m.name,
    kind = m.kind,
    address = m.address,
    area = m.area,
    latitude = m.latitude,
    longitude = m.longitude,
    phone = m.phone,
    specialties = m.specialties,
    badges = m.badges,
    updated_at = now()
FROM mapped m
WHERE p.id = m.id;

-- Add the remaining Da Nang entries after any preserved booked rows.
INSERT INTO place (name, kind, address, area, latitude, longitude, phone, specialties, badges)
SELECT s.name, s.kind, s.address, s.area, s.latitude, s.longitude, s.phone, s.specialties, s.badges
FROM _danang_places s
WHERE s.seed_no > (SELECT count(*) FROM place);
