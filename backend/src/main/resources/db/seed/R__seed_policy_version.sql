-- Draft Phase 1 documents. Legal review and replacement are required before go-live.
WITH seed(id, policy_type, title, content_md, affected_purposes) AS (
    VALUES
        ('01990000-0000-7000-8000-000000000001'::uuid,
         'TERMS',
         'Điều khoản sử dụng',
         E'# Điều khoản sử dụng\n\n> Bản nháp — chờ rà soát pháp lý.\n\nCatCheck hỗ trợ theo dõi thay đổi màu liên quan đến pH. Việc sử dụng dịch vụ phải tuân theo hướng dẫn trong ứng dụng.',
         ARRAY[]::varchar(48)[]),
        ('01990000-0000-7000-8000-000000000002'::uuid,
         'PRIVACY',
         'Chính sách quyền riêng tư',
         E'# Chính sách quyền riêng tư\n\n> Bản nháp — chờ rà soát pháp lý.\n\nCatCheck chỉ xử lý dữ liệu theo các mục đích bạn lựa chọn. Bạn có thể xem và thay đổi lựa chọn trong trung tâm quyền riêng tư.',
         ARRAY[
             'SERVICE_CORE', 'SCAN_IMAGE_RETAIN', 'ALGO_IMPROVEMENT',
             'HEALTH_REMINDER_PUSH', 'HEALTH_REMINDER_EMAIL', 'MARKETING_EMAIL',
             'MARKETING_PUSH', 'PRODUCT_ANALYTICS', 'COOKIE_ANALYTICS'
         ]::varchar(48)[]),
        ('01990000-0000-7000-8000-000000000003'::uuid,
         'MEDICAL_DISCLAIMER',
         'Tuyên bố miễn trừ y tế',
         E'# Tuyên bố miễn trừ y tế\n\n> Bản nháp — chờ rà soát pháp lý.\n\nCatCheck cung cấp thông tin tham khảo về pH, không thay thế việc thăm khám hoặc tư vấn của bác sĩ thú y.',
         ARRAY[]::varchar(48)[]),
        ('01990000-0000-7000-8000-000000000004'::uuid,
         'COOKIE',
         'Chính sách cookie',
         E'# Chính sách cookie\n\n> Bản nháp — chờ rà soát pháp lý.\n\nCatCheck chỉ dùng cookie cần thiết để ứng dụng hoạt động:\n\n- **Cookie phiên đăng nhập** (HttpOnly): giữ trạng thái đăng nhập của bạn trên thiết bị này; bị xoá khi bạn đăng xuất hoặc phiên hết hạn.\n- **Cookie `XSRF-TOKEN`**: mã bảo vệ chống giả mạo yêu cầu (CSRF) khi bạn thực hiện thao tác thay đổi dữ liệu.\n\nLựa chọn ngôn ngữ được lưu trong bộ nhớ trình duyệt (không phải cookie).\n\nHiện CatCheck không dùng cookie quảng cáo hay cookie theo dõi bên thứ ba. Nếu sau này bổ sung cookie đo lường, CatCheck sẽ chỉ đặt cookie đó khi bạn đồng ý.',
         ARRAY[]::varchar(48)[])
)
INSERT INTO policy_version (
    id, policy_type, version, locale, title, content_md, content_hash,
    summary_of_changes, requires_reconsent, affected_purposes, effective_from
)
SELECT
    id, policy_type, '1.0', 'vi', title, content_md,
    encode(digest(content_md, 'sha256'), 'hex'),
    'Bản placeholder cho MVP', false, affected_purposes,
    TIMESTAMPTZ '2026-01-01 00:00:00+07'
FROM seed
ON CONFLICT (policy_type, version, locale) DO UPDATE SET
    title = EXCLUDED.title,
    content_md = EXCLUDED.content_md,
    content_hash = EXCLUDED.content_hash,
    summary_of_changes = EXCLUDED.summary_of_changes,
    requires_reconsent = EXCLUDED.requires_reconsent,
    affected_purposes = EXCLUDED.affected_purposes,
    effective_from = EXCLUDED.effective_from;
