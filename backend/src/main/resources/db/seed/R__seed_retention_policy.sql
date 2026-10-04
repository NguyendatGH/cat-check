-- Seed `retention_policy` — thời hạn lưu là CẤU HÌNH, không hard-code
-- (p4 §4.9.2 bảng seed: `R__seed_retention_policy.sql`, "~18 chính sách theo p15 §15.5.1").
--
-- Nguồn: p15 §15.5.1 "Bảng retention (Phase 1)" quyết THỜI HẠN và HÀNH ĐỘNG; p12 §12.6 là
-- danh mục job duy nhất và quyết TÊN JOB (p15 §15.5.1 cố ý không còn cột lịch chạy).
-- REQ-RET-03: giá trị ở đây là GIÁ TRỊ SEED MẶC ĐỊNH; mọi thay đổi sau này do DPO thực hiện
-- (REQ-RET-06) và ghi `audit_log`.
--
-- ⚠ HAI DÒNG CỦA p15 §15.5.1 CỐ Ý CHƯA SEED, vì Phase 1 chưa có đích lưu để trỏ `target_table`:
--   · "Ảnh dataset cải thiện thuật toán" (D11, 24 tháng, `AlgoDatasetRetentionJob`) — p15 ghi
--     nơi lưu là "Bucket riêng (VN)", chưa có bảng/khoá nào ở Phase 1.
--   · "Access log / HTTP log" (D18, 90 ngày, `AccessLogRetentionJob`) — nơi lưu là "Log store
--     (VN)", là log ứng dụng, không phải bảng.
--   Cột tên là `target_table`: ghi một giá trị KHÔNG phải tên bảng vào đó sẽ làm chính trường
--   mà job retention dựa vào trở nên vô nghĩa. Bổ sung hai dòng này khi hạ tầng tương ứng có
--   thật. Ghi handoff H15.
--
-- ⚠ Ba dòng dưới đây lấy căn cứ từ chỗ KHÁC p15 §15.5.1, ghi rõ nguồn ở từng dòng:
--   · `POLICY_ACKNOWLEDGMENT_ARCHIVE` — p4 §4.6.5(c) bước 7 (cùng điều kiện với `consent_record`).
--   · `NOTIFICATION_SEND_LOG`         — p15 §15.2.2 dòng D17 ("CatCheck lưu log gửi 90 ngày").
--   · `SCAN_ANALYSIS_LIFECYCLE`       — p15 §15.5.1 dòng "Kết quả scan (D13)" (vòng đời tài
--     khoản ⇒ `retention_days` NULL, khử nhận dạng khi xoá tài khoản).
--
-- `safety_threshold_percent` để DEFAULT 20 (REQ-RET-02 + khoá `retention.safety_threshold_percent`).

INSERT INTO retention_policy (code, data_inventory_code, target_table, retention_days,
                              anchor_column, action_on_expiry, job_name, enabled, legal_basis)
VALUES
    -- Ảnh scan gốc: xoá FILE và mọi biến thể; GIỮ row với deleted_at + storage_key = NULL
    -- (p15 §15.5.1 ghi chú 1). File ảnh là thứ duy nhất là dữ liệu cá nhân nên hành động là
    -- HARD_DELETE; row còn lại chỉ chứa id, thời điểm và cờ đã xoá.
    ('SCAN_IMAGE_RAW', 'D10', 'scan_image', 14, 'created_at', 'HARD_DELETE',
     'ScanImageRetentionJob', true,
     'Đ3.3 + Đ14.1.c Luật BVDLCN; cam kết 14 ngày của owner (quyết định #9) đã in vào copy hiển thị cho user. Bất biến I8 + I15.'),

    ('PUSH_SUBSCRIPTION_IDLE', 'D16', 'push_subscription', 180, 'last_seen_at', 'HARD_DELETE',
     'CleanupDeadPushTokensJob', true,
     'Đ14.1.b Luật BVDLCN — hết mục đích xử lý khi thiết bị không còn hoạt động.'),

    ('PUSH_SUBSCRIPTION_REVOKED', 'D16', 'push_subscription', 30, 'revoked_at', 'HARD_DELETE',
     'CleanupDeadPushTokensJob', true,
     'Đ14.1.b Luật BVDLCN. Giữ thêm 30 ngày sau khi thu hồi chỉ để chẩn đoán lỗi gửi (p15 §15.5.1).'),

    -- Absolute lifetime 90 ngày của phiên (p11 §11.1.3). Cơ chế CHÍNH là `cleanupCron` của
    -- Spring Session JDBC; dòng này cấu hình cho vế lưới an toàn trong CleanupOtpAndSessionsJob.
    ('SESSION_ABSOLUTE_LIFETIME', NULL, 'spring_session', 90, 'creation_time', 'HARD_DELETE',
     'CleanupOtpAndSessionsJob', true,
     'Đ3.3 Luật BVDLCN; thời hạn phiên lấy từ p11 §11.1.3 (TD-01: phiên opaque server-side, không refresh token).'),

    ('EMAIL_OTP', NULL, 'email_otp', 1, 'expires_at', 'HARD_DELETE',
     'CleanupOtpAndSessionsJob', true,
     'Đ14.1.b Luật BVDLCN. Bản ghi giữ 24 giờ sau khi hết hạn để điều tra brute-force (p15 §15.5.1).'),

    ('PASSWORD_RESET', NULL, 'password_reset', 1, 'expires_at', 'HARD_DELETE',
     'CleanupOtpAndSessionsJob', true,
     'Đ14.1.b Luật BVDLCN. Bản ghi đã dùng/hết hạn giữ thêm 24 giờ (p15 §15.5.1).'),

    -- 72 giờ = 3 ngày, hoặc ngay sau lần tải đầu tiên — tuỳ điều kiện nào đến trước (p15 §15.4.5).
    ('DSAR_EXPORT_PACKAGE', NULL, 'dsar_request', 3, 'result_expires_at', 'HARD_DELETE',
     'DsarExportCleanupJob', true,
     'Đ4.1.d + Đ15.2.a Luật BVDLCN. Gói ZIP chứa TOÀN BỘ dữ liệu cá nhân của một người nên tồn đọng là rủi ro cao.'),

    ('ACTIVITY_LOG', 'D14', 'user_activity_log', 365, 'occurred_at', 'ANONYMIZE',
     'ActivityLogRetentionJob', true,
     'Đ3.3 Luật BVDLCN. Tổng hợp thành số liệu rồi khử nhận dạng (Đ2.1) — xem rủi ro phân loại Đ4.1.l ở p15 §15.2.4.'),

    ('AUDIT_LOG', 'D19', 'audit_log', 730, 'occurred_at', 'ARCHIVE',
     'AuditLogArchiveJob', true,
     'Đ37 + Đ19.1.a Luật BVDLCN — nghĩa vụ giải trình. Chuyển lưu trữ lạnh, verify checksum rồi mới xoá bản nóng.'),

    -- "Không tự xoá — rà soát thủ công hằng năm" (p15 §15.5.1) ⇒ không gắn job, enabled = false.
    ('SECURITY_INCIDENT', 'D20', 'security_incident', 1825, 'resolved_at', 'ARCHIVE',
     NULL, false,
     'Đ29.1.c NĐ356 — lưu hồ sơ sự cố ít nhất 05 năm kể từ ngày khắc phục xong. KHÔNG tự xoá, rà soát thủ công hằng năm.'),

    ('CONSENT_RECORD', 'D21', 'consent_record', 1825, 'created_at', 'HARD_DELETE',
     'ConsentArchiveJob', true,
     'Đ6.2 NĐ356 — nghĩa vụ chứng minh đồng ý. Vòng đời tài khoản + 05 năm (p15 §15.5.1, OQ-7).'),

    ('POLICY_ACKNOWLEDGMENT_ARCHIVE', 'D21', 'policy_acknowledgment', 1825, 'created_at', 'HARD_DELETE',
     'ConsentArchiveJob', true,
     'Đ6.2 NĐ356. p4 §4.6.5(c) bước 7 xếp policy_acknowledgment cùng điều kiện với consent_record (+05 năm).'),

    ('DSAR_REQUEST', NULL, 'dsar_request', 1825, 'received_at', 'HARD_DELETE',
     'DsarArchiveJob', true,
     'Chứng minh đã thực hiện quyền của chủ thể dữ liệu đúng hạn (Đ5.4 NĐ356). 05 năm (p15 §15.5.1).'),

    ('EMAIL_OUTBOX', NULL, 'email_outbox', 90, 'sent_at', 'HARD_DELETE',
     'MailLogRetentionJob', true,
     'Đ14.1.b Luật BVDLCN — log vận hành, hết mục đích sau 90 ngày.'),

    ('JOB_RUN', NULL, 'job_run', 90, 'started_at', 'HARD_DELETE',
     'MailLogRetentionJob', true,
     'Log vận hành, không chứa dữ liệu cá nhân. Giữ 90 ngày theo p15 §15.5.1.'),

    ('NOTIFICATION_SEND_LOG', 'D17', 'notification', 90, 'created_at', 'HARD_DELETE',
     'MailLogRetentionJob', true,
     'Đ14.1.b Luật BVDLCN. p15 §15.2.2 dòng D17: CatCheck lưu log gửi 90 ngày.'),

    -- Vòng đời tài khoản ⇒ retention_days NULL (không có mốc thời gian tuyệt đối). Việc khử
    -- nhận dạng xảy ra khi xoá tài khoản, theo bảng p15 §15.4.6 bước 3.
    ('SCAN_ANALYSIS_LIFECYCLE', 'D13', 'scan_analysis', NULL, 'computed_at', 'ANONYMIZE',
     'ErasureExecutionJob', true,
     'Đ2.1 Luật BVDLCN — dữ liệu sau khử nhận dạng không còn là dữ liệu cá nhân. Nếu user đã rút ALGO_IMPROVEMENT thì XOÁ CỨNG (p15 §15.4.6 bước 3).'),

    -- 24 tháng cảnh báo + 3 tháng khoá mềm + 12 tháng = 36 tháng (1095 ngày) kể từ lần đăng
    -- nhập cuối (p15 §15.5.1, p4 §4.6.5(d)).
    ('INACTIVE_ACCOUNT', 'D1', 'app_user', 1095, 'last_login_at', 'ANONYMIZE',
     'InactiveAccountJob', true,
     'Đ14.1.b Luật BVDLCN — xoá do hết mục đích xử lý, không theo yêu cầu nên không tạo dsar_request.'),

    -- Ân hạn 7 ngày rồi thực thi; hoàn tất trong hạn luật định 20 ngày (TD-05).
    ('ACCOUNT_DELETION_GRACE', 'D1', 'dsar_request', 7, 'received_at', 'ANONYMIZE',
     'ErasureExecutionJob', true,
     'Đ4.1.d + Đ14 Luật BVDLCN; Đ5.4 NĐ356 (hoàn tất ≤ 20 ngày). Bất biến I15: grace KHÔNG được nới quá 7 ngày.')

ON CONFLICT (code) DO UPDATE SET
    data_inventory_code = EXCLUDED.data_inventory_code,
    target_table        = EXCLUDED.target_table,
    retention_days      = EXCLUDED.retention_days,
    anchor_column       = EXCLUDED.anchor_column,
    action_on_expiry    = EXCLUDED.action_on_expiry,
    job_name            = EXCLUDED.job_name,
    enabled             = EXCLUDED.enabled,
    legal_basis         = EXCLUDED.legal_basis;

-- Nối ngược vòng FK thứ hai (p4 §4.9.3): `data_inventory_item.retention_policy_code`.
-- `R__seed_data_inventory_item.sql` chạy trước file này nên không tự đặt được giá trị.
UPDATE data_inventory_item d
SET retention_policy_code = m.policy_code
FROM (VALUES ('D1', 'INACTIVE_ACCOUNT'),
             ('D10', 'SCAN_IMAGE_RAW'),
             ('D13', 'SCAN_ANALYSIS_LIFECYCLE'),
             ('D14', 'ACTIVITY_LOG'),
             ('D16', 'PUSH_SUBSCRIPTION_IDLE'),
             ('D17', 'NOTIFICATION_SEND_LOG'),
             ('D19', 'AUDIT_LOG'),
             ('D20', 'SECURITY_INCIDENT'),
             ('D21', 'CONSENT_RECORD')) AS m(inventory_code, policy_code)
WHERE d.code = m.inventory_code
  AND d.retention_policy_code IS DISTINCT FROM m.policy_code;
