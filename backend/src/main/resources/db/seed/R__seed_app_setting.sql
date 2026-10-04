-- Seed `app_setting` — cấu hình runtime (p4 §4.9.2 bảng seed: `R__seed_app_setting.sql`).
--
-- Nguồn khoá và giá trị mặc định: p4 §H3 bảng "Khoá cấu hình Phase 1" (25 khoá) + p4 §4.3b.1
-- "app_setting — 6 khoá kill-switch (H1.7)" (6 khoá) = 31 khoá.
--
-- ⚠ p4 §4.9.2 mô tả file này là "19 khoá cấu hình ở H3". Con số 19 là SỐ CŨ: bảng H3 hiện liệt
-- kê 25 khoá, và §4.3b.1 (thêm sau, từ `03-arbitration.md` / handoff H1.7) thêm 6 kill-switch
-- nữa. Seed theo BẢNG (nguồn sự thật về từng khoá), không theo con số tóm tắt. Ghi handoff H15.
--
-- ⚠ `scan.min_result_confidence` được H3 ghi kiểu "NUMBER", nhưng `app_setting.value_type` chỉ
-- nhận STRING | INT | BOOL | JSON (p4 §4.4.8 + CHECK `ck_app_setting_value_type` ở V7). Dùng
-- `JSON` với giá trị JSONB số `0.40` — giá trị đúng, kiểu khai báo là giá trị hợp lệ gần nhất.
-- Ghi handoff H15 để p4/p14 chốt (thêm `NUMBER` vào enum, hay đổi H3 sang INT phần nghìn).
--
-- KHÔNG seed bí mật thật (FCM key, Cloudinary secret, DB password) — chúng thuộc biến môi
-- trường (p4 §H3 "Ghi chú nghiệp vụ", p18).
--
-- ⚠ QUY TẮC GHI ĐÈ — lệch có chủ ý khỏi "ON CONFLICT DO UPDATE" trần của p4 §4.9.2:
-- cột `value` CHỈ bị ghi đè khi `updated_by IS NULL`, tức khoá đó chưa từng được admin/DPO sửa.
-- Lý do: `app_setting.value` là TRẠNG THÁI VẬN HÀNH, không phải dữ liệu danh mục — 6 khoá
-- `feature.*` là kill-switch mà p4 §4.3b.1 yêu cầu "bấm được mà không restart app". Một file
-- seed ghi đè vô điều kiện sẽ BẬT LẠI tính năng vừa bị tắt khẩn cấp ngay ở lần deploy kế tiếp,
-- đúng lúc kill-switch cần có tác dụng nhất. Phần metadata (`value_type`, `description`,
-- `secret`) vẫn ghi đè vô điều kiện vì đó là tài liệu, không phải trạng thái. Ghi handoff H15.

INSERT INTO app_setting (key, value, value_type, description, secret)
VALUES
    -- ---- p4 §H3 — 25 khoá cấu hình Phase 1 -------------------------------------------------
    ('app.build_version', to_jsonb('0.0.0-local'::text), 'STRING',
     'Phiên bản build hiển thị ở footer app và badge "Phiên bản". Giá trị thật do CI ghi đè.', false),
    ('otp.ttl_seconds', to_jsonb(300), 'INT',
     'Hạn dùng của mã OTP email, tính bằng giây (p11 S5). Email gửi cho user đã ghi "5 phút".', false),
    ('otp.resend_cooldown_seconds', to_jsonb(60), 'INT',
     'Khoảng chờ tối thiểu giữa hai lần gửi lại OTP (p11 §11.2.4).', false),
    ('otp.max_attempts', to_jsonb(5), 'INT',
     'Số lần nhập sai OTP tối đa trước khi khoá mã (p11 S5).', false),
    ('password_reset.ttl_seconds', to_jsonb(1800), 'INT',
     'Hạn dùng của link đặt lại mật khẩu, tính bằng giây (p11 §11.3.4).', false),
    ('session.max_per_user', to_jsonb(10), 'INT',
     'Số phiên đăng nhập đồng thời tối đa của một tài khoản (p11 §11.1.3).', false),
    ('push.max_subscriptions_per_user', to_jsonb(10), 'INT',
     'Số đăng ký web push tối đa của một tài khoản (p8 PUSH_SUBSCRIPTION_LIMIT).', false),
    ('cat.max_per_user', to_jsonb(8), 'INT',
     'Trần cứng số hồ sơ mèo chưa xoá của một tài khoản (TD-06 / C31, bất biến I27).', false),
    ('scan.image_retention_days', to_jsonb(14), 'INT',
     'Số ngày giữ ảnh scan gốc (quyết định #9). Bất biến I15: KHÔNG được nới quá 14.', false),
    ('scan.reassign_window_hours', to_jsonb(24), 'INT',
     'Cửa sổ cho phép đổi mèo của một lần quét, tính từ captured_at (p6 §6.10.3).', false),
    ('scan.reassign_max_count', to_jsonb(3), 'INT',
     'Số lần đổi mèo tối đa cho một lần quét (p6 §6.10.3, bất biến I12).', false),
    ('scan.min_result_confidence', to_jsonb(0.40), 'JSON',
     'Ngưỡng confidence tối thiểu; dưới ngưỡng trả INCONCLUSIVE (p6 §6.7.1).', false),
    ('scan.trial_limit_per_account', to_jsonb(3), 'INT',
     'Số lần quét thử tối đa cho tài khoản chưa kích hoạt gói nào (p5 R6).', false),
    ('export.file_ttl_days', to_jsonb(7), 'INT',
     'Số ngày giữ file PDF đã xuất trước khi xoá (p4 G1).', false),
    ('export.max_range_months', to_jsonb(12), 'INT',
     'Khoảng thời gian tối đa của một lần xuất PDF, tính bằng tháng (p8 EXPORT_RANGE_TOO_LONG).', false),
    ('reminder.max_active_per_cat', to_jsonb(3), 'INT',
     'Số lịch nhắc đang bật tối đa của một hồ sơ mèo — một lịch cho mỗi type, 3 type.', false),
    ('account.deletion_grace_days', to_jsonb(7), 'INT',
     'Số ngày ân hạn trước khi thực thi xoá tài khoản (TD-05). Bất biến I15: KHÔNG được nới quá 7.', false),
    ('retention.safety_threshold_percent', to_jsonb(20), 'INT',
     'Ngưỡng an toàn của job retention: định xoá quá X% tổng bản ghi thì dừng (p15 REQ-RET-02).', false),
    ('credit.admin_adjust_max_per_operation', to_jsonb(200), 'INT',
     'Trần credit cho MỘT LẦN điều chỉnh thủ công của admin (p14 §14.4.4).', false),
    ('credit.admin_adjust_max_per_day', to_jsonb(1000), 'INT',
     'Trần credit MỖI NGÀY cho mỗi admin thao tác; vượt ⇒ CREDIT_ADJUST_LIMIT_EXCEEDED (p14 §14.4.4).', false),
    ('content.performance_claims_allowed', to_jsonb(false), 'BOOL',
     'Cờ mở khoá claim_type = PERFORMANCE. CHỈ bật sau khi p1 §1.8 có kết quả đo thật (p14 §14.3.5(d)).', false),
    ('content.blocked_terms',
     '["HIPAA","ISO/IEC 27001","ISO/Vet-17025","VET-SECURE","ISFM","Hội đồng Chăm sóc Tiết niệu Mèo","Feline Health Privacy"]'::jsonb,
     'JSON',
     'Nội dung care_tip chứa từ khoá trong danh sách này bị chặn publish (p14 §14.3.5(d), quyết định #5).', false),
    ('mfa.reset_request_ttl_hours', to_jsonb(24), 'INT',
     'Hạn của một user_mfa_reset_request chờ duyệt, tính bằng giờ (p14 §14.4.6 bước 9).', false),
    ('mfa.reset_max_per_admin_per_day', to_jsonb(3), 'INT',
     'Số yêu cầu reset TOTP tối đa mỗi admin được đề xuất trong một ngày (p11 §11.12.3).', false),
    ('disclaimer.medical_vi',
     to_jsonb('Đây là kết quả quan sát tại nhà, không phải chẩn đoán y khoa. Nếu bạn thấy lo lắng về bé, hãy hỏi ý kiến bác sĩ thú y.'::text),
     'STRING',
     'Disclaimer y tế D-SHORT (p15 §15.7.3) — hiển thị ở mọi màn kết quả và trong PDF. Bản nháp chờ luật sư duyệt.',
     false),

    -- ---- p4 §4.3b.1 — 6 kill-switch (H1.7, p11 §11.13.4 R5) --------------------------------
    ('feature.scan.enabled', to_jsonb(true), 'BOOL',
     'Kill-switch: tắt ⇒ POST /scans trả 503 FEATURE_DISABLED và KHÔNG trừ credit.', false),
    ('feature.registration.enabled', to_jsonb(true), 'BOOL',
     'Kill-switch: tắt ⇒ chặn đăng ký tài khoản mới.', false),
    ('feature.push.enabled', to_jsonb(true), 'BOOL',
     'Kill-switch: tắt ⇒ job gửi push bỏ qua, notification vẫn ghi status = SKIPPED.', false),
    ('feature.email.enabled', to_jsonb(true), 'BOOL',
     'Kill-switch: tắt ⇒ outbox ngừng đẩy nhưng KHÔNG xoá, gửi lại được sau.', false),
    ('feature.community.enabled', to_jsonb(true), 'BOOL',
     'Kill-switch: tắt ⇒ ẩn module cộng đồng (Phase 2).', false),
    ('feature.export.enabled', to_jsonb(true), 'BOOL',
     'Kill-switch: tắt ⇒ chặn tạo job PDF mới, job đang chạy vẫn hoàn tất.', false)

ON CONFLICT (key) DO UPDATE SET
    -- Chỉ nhận lại giá trị mặc định khi chưa có ai sửa khoá này (xem ghi chú đầu file).
    value       = CASE WHEN app_setting.updated_by IS NULL THEN EXCLUDED.value ELSE app_setting.value END,
    value_type  = EXCLUDED.value_type,
    description = EXCLUDED.description,
    secret      = EXCLUDED.secret,
    updated_at  = now();
