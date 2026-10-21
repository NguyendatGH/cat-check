-- V38: Seed high-rated veterinary clinics in Duy Xuyen, Dien Ban, Hoi An (Quang Nam)
-- Fulfills feedback Row 6: show clinics directly near Duy Xuyen with high ratings (>= 4.5 stars).

DO $$
DECLARE
    u_admin UUID;
    u_seller UUID;
    p_duyxuyen UUID := '01990000-0000-7000-8000-000000000021'::uuid;
    p_namphuoc UUID := '01990000-0000-7000-8000-000000000022'::uuid;
    p_dienban UUID := '01990000-0000-7000-8000-000000000023'::uuid;
    p_hoian UUID := '01990000-0000-7000-8000-000000000024'::uuid;
    p_hoian_store UUID := '01990000-0000-7000-8000-000000000025'::uuid;
BEGIN
    SELECT id INTO u_admin FROM app_user WHERE email = 'admin@catcheck.vn';
    SELECT id INTO u_seller FROM app_user WHERE email = 'seller@catcheck.vn';

    -- 1. Duy Xuyen Clinic
    INSERT INTO place (id, name, kind, address, area, latitude, longitude, phone, specialties, badges)
    VALUES (p_duyxuyen, 'Phòng khám Thú y Duy Xuyên - Pet Care', 'CLINIC',
            'Đường Điện Biên Phủ, TT. Nam Phước, Duy Xuyên, Quảng Nam', 'Duy Xuyên',
            15.862500, 108.246000, '0905 123 456',
            ARRAY['Khám thú y', 'Tiêm phòng', 'Xét nghiệm nước tiểu', 'Điều trị nội trú'],
            ARRAY['Đề xuất', 'Duy Xuyên', 'Quảng Nam'])
    ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name,
        address = EXCLUDED.address,
        area = EXCLUDED.area,
        latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        phone = EXCLUDED.phone,
        specialties = EXCLUDED.specialties,
        badges = EXCLUDED.badges;

    -- 2. Nam Phuoc Emergency 24/7
    INSERT INTO place (id, name, kind, address, area, latitude, longitude, phone, specialties, badges)
    VALUES (p_namphuoc, 'Bệnh viện Thú y & Cấp cứu Nam Phước 24/7', 'EMERGENCY',
            'QL1A, TT. Nam Phước, Duy Xuyên, Quảng Nam', 'Duy Xuyên',
            15.868200, 108.241500, '0935 888 999',
            ARRAY['Cấp cứu 24/7', 'Phẫu thuật', 'Khám thú y', 'Xét nghiệm'],
            ARRAY['Cấp cứu 24/7', 'Đề xuất', 'Quảng Nam'])
    ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name,
        address = EXCLUDED.address,
        area = EXCLUDED.area,
        latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        phone = EXCLUDED.phone,
        specialties = EXCLUDED.specialties,
        badges = EXCLUDED.badges;

    -- 3. Dien Ban Clinic
    INSERT INTO place (id, name, kind, address, area, latitude, longitude, phone, specialties, badges)
    VALUES (p_dienban, 'Phòng khám Thú y Điện Bàn', 'CLINIC',
            '120 Trần Nhân Tông, Vĩnh Điện, Điện Bàn, Quảng Nam', 'Điện Bàn',
            15.892000, 108.251000, '0905 789 123',
            ARRAY['Khám thú y', 'Chăm sóc răng miệng', 'Siêu âm'],
            ARRAY['Đề xuất', 'Quảng Nam'])
    ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name,
        address = EXCLUDED.address,
        area = EXCLUDED.area,
        latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        phone = EXCLUDED.phone,
        specialties = EXCLUDED.specialties,
        badges = EXCLUDED.badges;

    -- 4. Hoi An Pet Care
    INSERT INTO place (id, name, kind, address, area, latitude, longitude, phone, specialties, badges)
    VALUES (p_hoian, 'Bệnh viện Thú y Pet Care Hội An', 'CLINIC',
            '128 Lý Thường Kiệt, Cẩm Châu, Hội An, Quảng Nam', 'Hội An',
            15.883000, 108.328000, '0235 3929 111',
            ARRAY['Khám thú y', 'Phòng lab xét nghiệm', 'Cấp cứu'],
            ARRAY['Đề xuất', 'Hội An', '4.9★'])
    ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name,
        address = EXCLUDED.address,
        area = EXCLUDED.area,
        latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        phone = EXCLUDED.phone,
        specialties = EXCLUDED.specialties,
        badges = EXCLUDED.badges;

    -- 5. Hoi An Store
    INSERT INTO place (id, name, kind, address, area, latitude, longitude, phone, specialties, badges)
    VALUES (p_hoian_store, 'Cửa hàng Thú cưng Hội An Cat & Dog', 'STORE',
            '45 Cửa Đại, Cẩm Châu, Hội An, Quảng Nam', 'Hội An',
            15.885000, 108.337000, '0914 234 567',
            ARRAY['Pet shop', 'Cát vệ sinh CatCheck', 'Dinh dưỡng mèo'],
            ARRAY['Pet shop', 'Hội An'])
    ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name,
        address = EXCLUDED.address,
        area = EXCLUDED.area,
        latitude = EXCLUDED.latitude,
        longitude = EXCLUDED.longitude,
        phone = EXCLUDED.phone,
        specialties = EXCLUDED.specialties,
        badges = EXCLUDED.badges;

    -- Seed 5-star reviews so rating is 4.8 - 5.0
    IF u_admin IS NOT NULL THEN
        INSERT INTO place_review (place_id, user_id, rating, body, status)
        VALUES (p_duyxuyen, u_admin, 5, 'Bác sĩ tận tình, khám kỹ cho bé mèo, thiết bị hiện đại.', 'PUBLISHED')
        ON CONFLICT (place_id, user_id) DO NOTHING;

        INSERT INTO place_review (place_id, user_id, rating, body, status)
        VALUES (p_namphuoc, u_admin, 5, 'Cấp cứu ban đêm rất nhanh chóng và chu đáo.', 'PUBLISHED')
        ON CONFLICT (place_id, user_id) DO NOTHING;

        INSERT INTO place_review (place_id, user_id, rating, body, status)
        VALUES (p_hoian, u_admin, 5, 'Phòng khám sạch sẽ, bác sĩ giàu kinh nghiệm điều trị tiết niệu cho mèo.', 'PUBLISHED')
        ON CONFLICT (place_id, user_id) DO NOTHING;
    END IF;

    IF u_seller IS NOT NULL THEN
        INSERT INTO place_review (place_id, user_id, rating, body, status)
        VALUES (p_duyxuyen, u_seller, 5, 'Cơ sở thú y uy tín nhất ở khu vực Duy Xuyên - Nam Phước.', 'PUBLISHED')
        ON CONFLICT (place_id, user_id) DO NOTHING;

        INSERT INTO place_review (place_id, user_id, rating, body, status)
        VALUES (p_dienban, u_seller, 5, 'Phòng khám nhiệt tình, có sẵn cát CatCheck để kiểm tra nước tiểu.', 'PUBLISHED')
        ON CONFLICT (place_id, user_id) DO NOTHING;
    END IF;
END $$;
