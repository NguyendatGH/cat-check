-- V2: DB roles tối thiểu cho M0.
-- Chỉ tạo role ứng dụng "catcheck_app" (role chạy hằng ngày của app, dùng để kiểm tra quyền
-- hạn kết nối/GRANT sau này) + GRANT USAGE schema cơ bản, không lỗi khi migrate.
--
-- TODO(M1+, xem spec/parts/p4-domain-model-erd.md §4.6): bổ sung 3 role DB còn lại (đọc-chỉ,
-- migration, và role khác theo bảng quyền của p4) kèm DDL/GRANT chi tiết theo đúng tên và
-- phạm vi quyền mà p4 §4.6 quy định. Không bịa tên role ở migration hạ tầng này vì phần trích
-- spec cung cấp cho nhiệm vụ M0 không liệt kê tên 3 role đó.
--
-- Mật khẩu của role catcheck_app KHÔNG được đặt trong migration này (tránh secret trong code/
-- image) — được cấp phát ngoài băng bởi ops/infra (biến môi trường, ví dụ ALTER ROLE ... WITH
-- PASSWORD chạy thủ công/qua secret manager lúc provision DB), khớp nguyên tắc "prod chỉ biến
-- môi trường, không secret trong image" ở CLAUDE.md.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_app') THEN
        CREATE ROLE catcheck_app LOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO catcheck_app;
