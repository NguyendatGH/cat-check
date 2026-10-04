-- Seed `data_inventory_item` — bảng kiểm kê dữ liệu cá nhân D1…D21
-- (p4 §4.9.2 bảng seed: `R__seed_data_inventory_item.sql`, "D1…D21 theo p15 §15.2.2").
--
-- Nguồn: p15 §15.2.2 "Bảng kiểm kê". Phase 1 chỉ seed D1…D21; D22…D29 là Phase 2 (Community &
-- Map), Phase 3 (Commerce) và nhân sự nội bộ — p15 §15.2.2 liệt kê nhưng p4 §4.9.2 giới hạn
-- đúng D1…D21, và các bảng Phase 2/3 chưa có migration ở Phase 1.
--
-- QUY TẮC ÁNH XẠ (ghi rõ để người sau không phải đoán):
--   · `sensitivity`  : p15 "CB" -> BASIC, "NC" -> SENSITIVE. D11 là "KPL" (đã khử nhận dạng,
--     không còn là DLCN) nhưng vẫn phải nằm trong kiểm kê ⇒ để BASIC và nói rõ ở mô tả.
--   · `legal_basis`  : p15 "ĐY" -> CONSENT, "TT" -> CONTRACT, "PL" -> LEGAL_OBLIGATION,
--     "KC" -> VITAL_INTEREST. Cột chỉ chứa MỘT giá trị, nên với "ĐY + TT" lấy CONSENT theo
--     đúng chiến lược p15 §15.2.1: "CatCheck lấy consent cho gần như mọi mục đích, và chỉ dùng
--     TT/PL làm căn cứ dự phòng".
--   · `purpose_codes`: p15 §15.2.2 ghi mục đích bằng câu chữ, không bằng mã; ở đây quy chiếu
--     sang mã của p15 §15.3.2 (bảng `consent_purpose`). Mảng rỗng = mục đích không dựa trên
--     consent (căn cứ TT/PL).
--   · `cross_border` : p15 cột "Ra nước ngoài?". D2 được p15 ghi "Có — NẾU dùng email provider
--     nước ngoài"; để `true` vì khai dư luôn là hướng an toàn về tuân thủ (kích hoạt thêm rà
--     soát, không miễn trừ gì), và ghi điều kiện vào mô tả.
--
-- `retention_policy_code` để NULL ở file này: hai bảng tham chiếu vòng
-- (`retention_policy.data_inventory_code` ↔ `data_inventory_item.retention_policy_code`,
-- p4 §4.9.3). Flyway chạy các file `R__` theo thứ tự tên nên file này chạy TRƯỚC
-- `R__seed_retention_policy.sql`; chính file retention đó nối ngược lại bằng một câu UPDATE.

INSERT INTO data_inventory_item (code, category_vi, description_vi, description_en,
                                 sensitivity, legal_basis, purpose_codes,
                                 storage_location, cross_border, recipient, active)
VALUES
    ('D1', 'Tài khoản',
     'Họ tên / tên hiển thị (`app_user.full_name`) — định danh tài khoản, xưng hô trong UI và PDF.',
     'Account holder full name / display name.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'PostgreSQL (VN)', false, NULL, true),

    ('D2', 'Tài khoản',
     'Email (`app_user.email`) — đăng nhập, OTP xác thực, thông báo hệ thống, khôi phục mật khẩu. '
         || 'Chuyển xuyên biên giới NẾU dùng email provider nước ngoài (p15 §15.6.4) — rà lại khi chốt nhà cung cấp.',
     'Account email address.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'PostgreSQL (VN)', true, 'Nhà cung cấp gửi email (chưa chốt — p15 §15.6.4)', true),

    ('D3', 'Tài khoản',
     'Số điện thoại (`app_user.phone`, tuỳ chọn) — liên hệ hỗ trợ, xác minh mã kích hoạt. Mã hoá at-rest.',
     'Optional phone number.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'PostgreSQL (VN)', false, NULL, true),

    ('D4', 'Tài khoản',
     'Hash mật khẩu (`app_user.password_hash`) — xác thực.',
     'Password hash.',
     'BASIC', 'CONTRACT', ARRAY []::varchar(48)[],
     'PostgreSQL (VN)', false, NULL, true),

    ('D5', 'Tài khoản',
     'Định danh Google OAuth (`google_sub`, `google_email`, `google_picture_url`) — đăng nhập Google.',
     'Google OAuth identifiers.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'PostgreSQL (VN)', true, 'Google LLC (OAuth)', true),

    ('D6', 'Tài khoản',
     'Ảnh đại diện người dùng — hình ảnh của cá nhân (Đ3.6 NĐ356).',
     'User avatar image.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'Local storage (VN)', false, NULL, true),

    ('D7', 'Hồ sơ mèo',
     'Thông tin hồ sơ mèo (`cat.name`, giống, giới tính, ngày sinh/tuổi, cân nặng, triệt sản) — '
         || 'tạo hồ sơ theo dõi riêng từng bé, dựng baseline cá thể.',
     'Cat profile attributes.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'PostgreSQL (VN)', false, NULL, true),

    ('D8', 'Hồ sơ mèo',
     'Ảnh đại diện của mèo (`cat.avatar`) — nhận diện hồ sơ trong UI. Xem cảnh báo p15 §15.2.5.',
     'Cat avatar image.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'Local storage (VN)', false, NULL, true),

    ('D9', 'Hồ sơ mèo',
     'Khảo sát sức khoẻ lúc onboarding (bệnh nền, chế độ ăn, tần suất đi vệ sinh…) — cá nhân hoá '
         || 'baseline và rule cảnh báo. Là dữ liệu của MÈO, KHÔNG phải dữ liệu sức khoẻ của người (p15 §15.2.3).',
     'Cat onboarding health survey.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'PostgreSQL (VN)', false, NULL, true),

    ('D10', 'Ảnh chụp cát',
     'File ảnh gốc do user tải lên — phân tích màu để ước lượng pH, cho user xem lại/khiếu nại kết quả. '
         || 'EXIF bị gỡ trước khi lưu; nếu còn EXIF GPS thì trở thành dữ liệu nhạy cảm Đ4.1.h.',
     'Raw litter photo uploaded by the user.',
     'BASIC', 'CONSENT', ARRAY ['SCAN_IMAGE_RETAIN']::varchar(48)[],
     'Thư mục local trên server VN (Phase 1)', false, NULL, true),

    ('D11', 'Ảnh chụp cát',
     'Bản sao ảnh dùng cải thiện thuật toán — KHÔNG còn là dữ liệu cá nhân sau khi khử nhận dạng (Đ2.1 Luật BVDLCN), '
         || 'nhưng vẫn kiểm kê vì bản thân việc khử nhận dạng là một mục đích xử lý cần consent riêng.',
     'De-identified image copy used to tune the colour pipeline.',
     'BASIC', 'CONSENT', ARRAY ['ALGO_IMPROVEMENT']::varchar(48)[],
     'Bucket riêng (VN)', false, NULL, true),

    ('D12', 'Ảnh chụp cát',
     'Metadata ảnh: `taken_at`, `device_hint`, kích thước, cờ `exif_stripped` — chẩn đoán lỗi chụp, hướng dẫn user.',
     'Scan image metadata.',
     'BASIC', 'CONTRACT', ARRAY []::varchar(48)[],
     'PostgreSQL (VN)', false, NULL, true),

    ('D13', 'Kết quả scan',
     'Kết quả phân tích: `ph_value`, `ph_band`, `classification`, Lab trung gian, `confidence`, '
         || '`warning_flags`, thời điểm quét, `cat_id` — hiển thị kết quả, lịch sử, xu hướng, cảnh báo, xuất PDF.',
     'Scan analysis results.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'PostgreSQL (VN)', false, NULL, true),

    ('D14', 'Lịch sử dùng app',
     'Log hành vi trong sản phẩm: màn hình đã mở, tính năng đã dùng, thời điểm, tần suất quét. '
         || 'RỦI RO bị xếp dữ liệu nhạy cảm theo Đ4.1.l — xem p15 §15.2.4.',
     'In-product activity log.',
     'BASIC', 'CONSENT', ARRAY ['PRODUCT_ANALYTICS']::varchar(48)[],
     'PostgreSQL / analytics tự vận hành (VN)', false, NULL, true),

    ('D15', 'Credit & entitlement',
     'Lô credit, sổ cái credit, lượt đổi mã kích hoạt, gói đang dùng — cấp phát và tiêu credit, gating tính năng.',
     'Credit batches, ledger and entitlement.',
     'BASIC', 'CONTRACT', ARRAY []::varchar(48)[],
     'PostgreSQL (VN)', false, NULL, true),

    ('D16', 'Push notification',
     'Đăng ký nhận push (`push_subscription`): `fid`/`legacy_token`, nhãn thiết bị, user agent, `last_seen_at`.',
     'Web push subscription records.',
     'BASIC', 'CONSENT', ARRAY ['HEALTH_REMINDER_PUSH']::varchar(48)[],
     'PostgreSQL (VN) + hạ tầng Google', true, 'Google LLC (Firebase Cloud Messaging)', true),

    ('D17', 'Push notification',
     'Nội dung payload thông báo gửi đi — truyền tải nội dung nhắc. CatCheck giữ log gửi 90 ngày.',
     'Push notification payload.',
     'BASIC', 'CONSENT', ARRAY ['HEALTH_REMINDER_PUSH']::varchar(48)[],
     'Log (VN) + transit qua Google', true, 'Google LLC (Firebase Cloud Messaging)', true),

    ('D18', 'Log & bảo mật',
     'Access log: địa chỉ IP, user agent, `request_id`, thời điểm, endpoint, mã lỗi — bảo mật, chống lạm dụng, '
         || 'điều tra sự cố, rate limit (Đ19.1.a).',
     'HTTP access and security logs.',
     'BASIC', 'CONTRACT', ARRAY []::varchar(48)[],
     'Log store (VN)', false, NULL, true),

    ('D19', 'Log & bảo mật',
     '`audit_log` — nhật ký admin truy cập dữ liệu người dùng; chứng minh tuân thủ và truy vết nội bộ (Đ37).',
     'Admin access audit log.',
     'BASIC', 'CONTRACT', ARRAY []::varchar(48)[],
     'PostgreSQL (VN)', false, NULL, true),

    ('D20', 'Log & bảo mật',
     '`security_incident` — hồ sơ sự cố lộ/mất dữ liệu; nghĩa vụ báo cáo và lưu hồ sơ (Đ29.1.c NĐ356).',
     'Security incident records.',
     'BASIC', 'LEGAL_OBLIGATION', ARRAY []::varchar(48)[],
     'PostgreSQL + lưu trữ ngoại tuyến (VN)', false, NULL, true),

    ('D21', 'Consent',
     '`consent_record` — bằng chứng đồng ý. Nghĩa vụ chứng minh thuộc về CatCheck (Đ6.2 NĐ356).',
     'Consent evidence records.',
     'BASIC', 'LEGAL_OBLIGATION', ARRAY []::varchar(48)[],
     'PostgreSQL (VN)', false, NULL, true)

ON CONFLICT (code) DO UPDATE SET
    category_vi      = EXCLUDED.category_vi,
    description_vi   = EXCLUDED.description_vi,
    description_en   = EXCLUDED.description_en,
    sensitivity      = EXCLUDED.sensitivity,
    legal_basis      = EXCLUDED.legal_basis,
    purpose_codes    = EXCLUDED.purpose_codes,
    storage_location = EXCLUDED.storage_location,
    cross_border     = EXCLUDED.cross_border,
    recipient        = EXCLUDED.recipient,
    active           = EXCLUDED.active;
