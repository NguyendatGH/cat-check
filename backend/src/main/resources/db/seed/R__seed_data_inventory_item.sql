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
     'Họ tên / tên hiển thị — định danh tài khoản và xưng hô trong ứng dụng, báo cáo PDF.',
     'Account holder full name / display name.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'Cơ sở dữ liệu đặt tại Việt Nam', false, NULL, true),

    ('D2', 'Tài khoản',
     'Email — đăng nhập, mã xác thực (OTP), thông báo hệ thống, khôi phục mật khẩu. '
         || 'Có thể được chuyển ra nước ngoài nếu nhà cung cấp dịch vụ gửi email đặt máy chủ ngoài Việt Nam.',
     'Account email address.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'Cơ sở dữ liệu đặt tại Việt Nam', true, 'Nhà cung cấp dịch vụ gửi email', true),

    ('D3', 'Tài khoản',
     'Số điện thoại (tuỳ chọn) — liên hệ hỗ trợ, xác minh mã kích hoạt. Được mã hoá khi lưu trữ.',
     'Optional phone number.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'Cơ sở dữ liệu đặt tại Việt Nam', false, NULL, true),

    ('D4', 'Tài khoản',
     'Mật khẩu (chỉ lưu dạng băm một chiều, không lưu mật khẩu gốc) — dùng để xác thực đăng nhập.',
     'Password hash.',
     'BASIC', 'CONTRACT', ARRAY []::varchar(48)[],
     'Cơ sở dữ liệu đặt tại Việt Nam', false, NULL, true),

    ('D5', 'Tài khoản',
     'Thông tin định danh từ tài khoản Google (mã tài khoản, email, ảnh) — dùng khi đăng nhập bằng Google.',
     'Google OAuth identifiers.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'Cơ sở dữ liệu đặt tại Việt Nam', true, 'Google LLC (OAuth)', true),

    ('D6', 'Tài khoản',
     'Ảnh đại diện người dùng — hình ảnh của cá nhân (Đ3.6 NĐ356).',
     'User avatar image.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'Máy chủ lưu trữ tại Việt Nam', false, NULL, true),

    ('D7', 'Hồ sơ mèo',
     'Thông tin hồ sơ mèo (tên, giống, giới tính, ngày sinh hoặc tuổi, cân nặng, triệt sản) — '
         || 'tạo hồ sơ theo dõi riêng từng bé và làm mức tham chiếu cho từng cá thể.',
     'Cat profile attributes.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'Cơ sở dữ liệu đặt tại Việt Nam', false, NULL, true),

    ('D8', 'Hồ sơ mèo',
     'Ảnh đại diện của mèo — nhận diện hồ sơ trong ứng dụng. Bạn nên chọn ảnh không có người hoặc thông tin cá nhân.',
     'Cat avatar image.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'Máy chủ lưu trữ tại Việt Nam', false, NULL, true),

    ('D9', 'Hồ sơ mèo',
     'Khảo sát lúc tạo hồ sơ (tình trạng sẵn có, chế độ ăn, tần suất đi vệ sinh…) — cá nhân hoá '
         || 'mức tham chiếu và quy tắc nhắc nhở. Đây là thông tin về mèo, không phải thông tin sức khoẻ của người dùng.',
     'Cat onboarding health survey.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'Cơ sở dữ liệu đặt tại Việt Nam', false, NULL, true),

    ('D10', 'Ảnh chụp cát',
     'Ảnh gốc bạn tải lên — dùng phân tích màu để ước lượng pH, cho bạn xem lại hoặc phản hồi kết quả. '
         || 'Thông tin vị trí và metadata EXIF được gỡ khỏi ảnh trước khi lưu.',
     'Raw litter photo uploaded by the user.',
     'BASIC', 'CONSENT', ARRAY ['SCAN_IMAGE_RETAIN']::varchar(48)[],
     'Máy chủ lưu trữ tại Việt Nam', false, NULL, true),

    ('D11', 'Ảnh chụp cát',
     'Bản sao ảnh đã khử nhận dạng dùng để cải thiện thuật toán nhận diện màu. Chỉ thực hiện khi bạn đồng ý riêng '
         || 'cho mục đích này; bạn có thể rút lại đồng ý bất cứ lúc nào.',
     'De-identified image copy used to tune the colour pipeline.',
     'BASIC', 'CONSENT', ARRAY ['ALGO_IMPROVEMENT']::varchar(48)[],
     'Kho lưu trữ riêng tại Việt Nam', false, NULL, true),

    ('D12', 'Ảnh chụp cát',
     'Thông tin kỹ thuật của ảnh (thời điểm chụp, gợi ý loại thiết bị, kích thước, đã gỡ EXIF hay chưa) — chẩn đoán lỗi chụp và hướng dẫn bạn chụp lại.',
     'Scan image metadata.',
     'BASIC', 'CONTRACT', ARRAY []::varchar(48)[],
     'Cơ sở dữ liệu đặt tại Việt Nam', false, NULL, true),

    ('D13', 'Kết quả scan',
     'Kết quả phân tích: giá trị pH ước lượng, nhóm phân loại, độ tin cậy, các cảnh báo chất lượng ảnh, '
         || 'thời điểm quét và bé mèo được quét — hiển thị kết quả, lịch sử, xu hướng, nhắc nhở và xuất báo cáo PDF.',
     'Scan analysis results.',
     'BASIC', 'CONSENT', ARRAY ['SERVICE_CORE']::varchar(48)[],
     'Cơ sở dữ liệu đặt tại Việt Nam', false, NULL, true),

    ('D14', 'Lịch sử dùng app',
     'Nhật ký sử dụng ứng dụng: màn hình đã mở, tính năng đã dùng, thời điểm, tần suất quét — '
         || 'chỉ thu thập khi bạn đồng ý, dùng để cải thiện sản phẩm.',
     'In-product activity log.',
     'BASIC', 'CONSENT', ARRAY ['PRODUCT_ANALYTICS']::varchar(48)[],
     'Hệ thống phân tích tự vận hành tại Việt Nam', false, NULL, true),

    ('D15', 'Credit & entitlement',
     'Lô credit, sổ cái credit, lượt đổi mã kích hoạt, gói đang dùng — cấp phát và tiêu credit, gating tính năng.',
     'Credit batches, ledger and entitlement.',
     'BASIC', 'CONTRACT', ARRAY []::varchar(48)[],
     'Cơ sở dữ liệu đặt tại Việt Nam', false, NULL, true),

    ('D16', 'Push notification',
     'Đăng ký nhận thông báo đẩy: mã định danh thiết bị, nhãn thiết bị, trình duyệt và lần hoạt động gần nhất.',
     'Web push subscription records.',
     'BASIC', 'CONSENT', ARRAY ['HEALTH_REMINDER_PUSH']::varchar(48)[],
     'Cơ sở dữ liệu tại Việt Nam và hạ tầng Google', true, 'Google LLC (Firebase Cloud Messaging)', true),

    ('D17', 'Push notification',
     'Nội dung thông báo gửi đến thiết bị của bạn (nhắc nhở). CatCheck giữ nhật ký gửi 90 ngày.',
     'Push notification payload.',
     'BASIC', 'CONSENT', ARRAY ['HEALTH_REMINDER_PUSH']::varchar(48)[],
     'Nhật ký tại Việt Nam, truyền qua hạ tầng Google', true, 'Google LLC (Firebase Cloud Messaging)', true),

    ('D18', 'Log & bảo mật',
     'Nhật ký truy cập: địa chỉ IP, trình duyệt, thời điểm, đường dẫn và mã lỗi — bảo mật, chống lạm dụng, '
         || 'điều tra sự cố và giới hạn tần suất yêu cầu.',
     'HTTP access and security logs.',
     'BASIC', 'CONTRACT', ARRAY []::varchar(48)[],
     'Hệ thống nhật ký tại Việt Nam', false, NULL, true),

    ('D19', 'Log & bảo mật',
     'Nhật ký các thao tác của quản trị viên trên dữ liệu người dùng — chứng minh tuân thủ và truy vết nội bộ. Bạn có thể xem các lần truy cập vào dữ liệu của mình.',
     'Admin access audit log.',
     'BASIC', 'CONTRACT', ARRAY []::varchar(48)[],
     'Cơ sở dữ liệu đặt tại Việt Nam', false, NULL, true),

    ('D20', 'Log & bảo mật',
     'Hồ sơ sự cố lộ hoặc mất dữ liệu — nghĩa vụ báo cáo và lưu hồ sơ theo quy định (Đ29.1.c NĐ356).',
     'Security incident records.',
     'BASIC', 'LEGAL_OBLIGATION', ARRAY []::varchar(48)[],
     'Cơ sở dữ liệu và lưu trữ ngoại tuyến tại Việt Nam', false, NULL, true),

    ('D21', 'Consent',
     'Bản ghi các lựa chọn đồng ý của bạn — CatCheck có nghĩa vụ chứng minh việc đã được đồng ý (Đ6.2 NĐ356).',
     'Consent evidence records.',
     'BASIC', 'LEGAL_OBLIGATION', ARRAY []::varchar(48)[],
     'Cơ sở dữ liệu đặt tại Việt Nam', false, NULL, true)

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
