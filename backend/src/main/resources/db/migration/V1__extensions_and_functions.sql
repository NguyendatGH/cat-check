-- V1: PostgreSQL extensions cơ bản + helper functions dùng chung cho toàn schema.
-- Nguồn: spec/parts/p4-domain-model-erd.md §4.9.2 (M0 - hạ tầng schema, chưa có bảng nghiệp vụ).

CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Trigger function chuẩn: tự động cập nhật cột updated_at mỗi khi row được UPDATE.
-- Áp dụng ở các migration nghiệp vụ sau (M1+) bằng:
--   CREATE TRIGGER trg_<table>_updated_at BEFORE UPDATE ON <table>
--     FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- Polyfill uuidv7() cho PostgreSQL < 18.
--
-- PostgreSQL 18 có hàm uuidv7() native trong schema pg_catalog. pg_catalog luôn được tìm
-- trước tiên trong search_path (hành vi mặc định của Postgres, không phụ thuộc cấu hình
-- search_path của session), nên trên PostgreSQL 18.6 (phiên bản đã chốt cho CatCheck) hàm
-- native sẽ tự động được ưu tiên khi gọi uuidv7() không schema-qualify; hàm polyfill dưới
-- đây trong schema public chỉ là fallback an toàn cho các môi trường chạy image Postgres < 18
-- (ví dụ image CI/test chưa được ghim đúng phiên bản).
--
-- Bug thật đã sửa: PHẢI schema-qualify "public.uuidv7()" ở CREATE OR REPLACE — để trần thì
-- Postgres phân giải tên qua chính search_path nói ở trên và thấy pg_catalog.uuidv7() TRÙNG
-- signature trước, coi đó là hàm cần REPLACE, rồi từ chối vì role ứng dụng không phải chủ sở
-- hữu pg_catalog.uuidv7() (thuộc superuser hệ thống). Superuser thì owns-everything nên không
-- thấy lỗi này (chính là lý do lọt qua: mọi lần test trước đều dùng role Postgres image Docker
-- mặc định — cấp SUPERUSER tự động cho POSTGRES_USER, khác role ứng dụng thật KHÔNG superuser).
-- Xác nhận thật: role `catcheck` (LOGIN thường, không SUPERUSER) trên chính PostgreSQL 18.6 ném
-- "ERROR: must be owner of function uuidv7" đúng ở statement CREATE OR REPLACE này.
-- Kỹ thuật: lấy 16 byte từ gen_random_uuid() (pgcrypto), ghi đè 6 byte đầu bằng Unix epoch
-- millisecond hiện tại (big-endian, qua int8send + substring), rồi set 4 bit version = 0111
-- và 2 bit variant = 10 đúng bố cục RFC 9562 §5.7 cho UUID version 7. Kỹ thuật overlay/set_bit
-- này là một polyfill phổ biến đã lưu hành rộng rãi trong cộng đồng PostgreSQL trước khi bản 18
-- có hỗ trợ native; ghi rõ nguồn ở đây để đảm bảo minh bạch, KHÔNG tự nhận là tác giả gốc.
CREATE OR REPLACE FUNCTION public.uuidv7()
RETURNS uuid
LANGUAGE sql
VOLATILE
AS $$
    SELECT encode(
        set_bit(
            set_bit(
                overlay(
                    uuid_send(gen_random_uuid()) placing
                    substring(int8send((extract(epoch FROM clock_timestamp()) * 1000)::bigint) FROM 3)
                    FROM 1 FOR 6
                ),
                52, 1
            ),
            53, 1
        ),
        'hex'
    )::uuid;
$$;

COMMENT ON FUNCTION set_updated_at() IS 'Trigger function chuẩn: NEW.updated_at = now(); dùng cho mọi bảng có cột updated_at.';
COMMENT ON FUNCTION public.uuidv7() IS 'Polyfill UUIDv7 (RFC 9562) cho PostgreSQL < 18. Trên PG18+ hàm native pg_catalog.uuidv7() được ưu tiên tự động.';
