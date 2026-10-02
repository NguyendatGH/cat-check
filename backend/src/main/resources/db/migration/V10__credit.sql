-- V10: Credit & Entitlement — nhóm E của spec/parts/p4-domain-model-erd.md.
--
-- Danh mục migration: p4 §4.9.2 (V10 = activation_code, credit_batch, credit_ledger,
-- user_entitlement + CHECK dấu theo type + REVOKE UPDATE, DELETE). Phụ thuộc V5 (app_user,
-- module identity) và V7 (package_plan, module cat/content) — Cả hai KHÔNG tạo ở file này.
--
-- Quy ước áp dụng (p4 §4.1):
--   §4.1.1  UUID v7 sinh ở tầng ứng dụng (shared.id.UuidV7) — không đặt DEFAULT uuidv7() ở đây
--            để không phụ thuộc thứ tự version migration.
--   §4.1.2  Mọi cột thời gian là TIMESTAMPTZ. Mọi bảng có created_at/updated_at, trừ
--            credit_ledger (append-only, p4 §4.1.2 ngoại lệ "năm bảng append-only").
--            Trigger set_updated_at() áp cho mọi bảng ở V16 (W3), KHÔNG tạo ở đây.
--   §4.1.3  Enum = VARCHAR + CHECK, KHÔNG dùng kiểu ENUM của PostgreSQL.
--   §4.1.4  credit_ledger / credit_batch / activation_code = chứng từ đối soát, KHÔNG xoá bao giờ.
--
-- Ngữ nghĩa nghiệp vụ (FEFO, hết hạn theo lô, entitlement không hết hạn cùng credit) do
-- p5 §5.4–§5.6 quyết định. Ở file này chỉ ép những gì p4 §4.5 liệt kê là ép ở tầng DB.

-- ============================================================================
-- 1. activation_code — mã in trên bao bì, dùng một lần, đổi lấy một credit_batch
-- ============================================================================
-- LƯU Ý BẢO MẬT: `code_hash` là HMAC-SHA256(ACTIVATION_PEPPER, code) — KHÔNG phải SHA-256
-- trần. p11 §11.7.4: không gian mã chỉ ~3,5 × 10¹³ tổ hợp hợp lệ checksum, SHA-256 không
-- pepper là duyệt hết được bằng một GPU trong khoảng một giờ nếu rò DB, mà mỗi mã là credit
-- thật đã in trên bao bì. `pepper_version` là BẮT BUỘC đi kèm: mã thô không còn tồn tại ở đâu
-- (chỉ xuất một lần ra CSV cho khâu in) nên xoay pepper mà không verify được nhiều version sẽ
-- làm mọi mã chưa đổi vô hiệu vĩnh viễn.
--
-- `code_hash` là CHAR(64) chứ không VARCHAR(64): độ dài hex cố định nên CHAR so sánh byte
-- và index nhỏ hơn (p4 bảng "Điều chỉnh kiểu dữ liệu so với Part 5" cho group E).
CREATE TABLE activation_code (
    id               UUID PRIMARY KEY,
    code_hash        CHAR(64)     NOT NULL UNIQUE,
    pepper_version   SMALLINT     NOT NULL DEFAULT 1,
    -- Tiền tố để hỗ trợ tra cứu khi user đọc sai vài ký tự, ví dụ 'PLUS-'. Tối đa 8 ký tự
    -- (p5 §5.5); mã gói dài hơn bị cắt còn 8 — cột này CHỈ là gợi ý tra cứu, không bao giờ
    -- là khoá tra cứu (khoá tra cứu là code_hash).
    code_prefix      VARCHAR(8)   NOT NULL,
    package_code     VARCHAR(32)  NOT NULL REFERENCES package_plan (code),
    -- Lô sản xuất, liên kết bảng màu pH (color_chart.production_batch) — p5 §5.5.
    production_batch VARCHAR(64),
    issued_at        TIMESTAMPTZ  NOT NULL,
    -- Hạn KÍCH HOẠT, khác hạn credit (credit_validity_days tính từ activated_at) — p5 §5.5.
    valid_until      TIMESTAMPTZ,
    status           VARCHAR(16)  NOT NULL,
    redeemed_by      UUID         REFERENCES app_user (id),
    redeemed_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_activation_code_status
        CHECK (status IN ('ISSUED', 'REDEEMED', 'VOID')),
    CONSTRAINT ck_activation_code_pepper_version
        CHECK (pepper_version >= 1)
);

-- Admin tra lô mã đã phát hành theo gói (p14 §14.4.1).
CREATE INDEX ix_activation_code_package
    ON activation_code (package_code, status, issued_at DESC);
-- Hỗ trợ khách hàng tra theo tiền tố khi user đọc sai vài ký tự (p4 group E, index bổ sung).
CREATE INDEX ix_activation_code_prefix
    ON activation_code (code_prefix, status);
-- Tra lô sản xuất khi cần đối soát/liên kết bảng màu.
CREATE INDEX ix_activation_code_production_batch
    ON activation_code (production_batch)
    WHERE production_batch IS NOT NULL;

-- ============================================================================
-- 2. credit_batch — một lô credit sinh ra từ một lần kích hoạt, có expires_at riêng
-- ============================================================================
CREATE TABLE credit_batch (
    id                 UUID PRIMARY KEY,
    user_id            UUID        NOT NULL REFERENCES app_user (id),
    -- UNIQUE: một mã đổi ra đúng một lô (bất biến I24, p4 §4.5.1).
    activation_code_id UUID        UNIQUE REFERENCES activation_code (id),
    -- Snapshot cấu hình gói tại thời điểm kích hoạt (p5 R1) — KHÔNG tham chiếu động,
    -- nên cố ý không có FK: đổi package_plan sau đó không được hồi tố batch đã phát hành (C12).
    package_code       VARCHAR(32) NOT NULL,
    package_version    INT         NOT NULL,
    initial_amount     INT         NOT NULL,
    remaining_amount   INT         NOT NULL,
    activated_at       TIMESTAMPTZ NOT NULL,
    expires_at         TIMESTAMPTZ NOT NULL,
    status             VARCHAR(16) NOT NULL,
    -- Bộ lọc nhanh cho CreditExpiringReminderJob chạy mỗi giờ (p12 §12.6). Bảo đảm không
    -- gửi trùng vẫn là notification.dedupe_key UNIQUE ở mức DB, không phải hai cột này.
    t48h_notified_at   TIMESTAMPTZ,
    t6h_notified_at    TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_credit_batch_status
        CHECK (status IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED')),
    -- Bất biến I2 — lưới an toàn cuối cùng khi hai request đồng thời cùng trừ (p4 §4.5.1, p5 §5.6).
    CONSTRAINT ck_credit_batch_amounts
        CHECK (remaining_amount >= 0 AND remaining_amount <= initial_amount AND initial_amount > 0),
    CONSTRAINT ck_credit_batch_expiry
        CHECK (expires_at > activated_at)
);

-- Truy vấn FEFO (chọn lô sắp hết hạn nhất) + SELECT ... FOR UPDATE theo thứ tự cố định để
-- tránh deadlock (p4 group E; index này phục vụ đúng cả hai).
CREATE INDEX ix_credit_batch_user_status_expires
    ON credit_batch (user_id, status, expires_at);
-- Index partial cho ExpireCreditBatchesJob chạy mỗi giờ — chỉ chứa lô còn sống nên rất nhỏ.
CREATE INDEX ix_credit_batch_expiring
    ON credit_batch (expires_at)
    WHERE status = 'ACTIVE' AND remaining_amount > 0;

-- ============================================================================
-- 3. credit_ledger — sổ cái append-only, mọi biến động credit
-- ============================================================================
-- KHÔNG có updated_at (p4 §4.1.2: năm bảng append-only không có updated_at, không có
-- trigger). Bị chặn UPDATE/DELETE bằng QUYỀN DB, không bằng kỷ luật (p4 §4.6.2).
CREATE TABLE credit_ledger (
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL,
    batch_id        UUID        REFERENCES credit_batch (id),
    type            VARCHAR(16) NOT NULL,
    -- Dương = vào, âm = ra. Dấu bị ràng buộc bởi ck_credit_ledger_sign bên dưới.
    amount          INT         NOT NULL,
    -- Số dư khả dụng TOÀN USER sau giao dịch = SUM(remaining_amount) của các lô
    -- status='ACTIVE' AND expires_at > now (p5 R4).
    balance_after   INT         NOT NULL,
    -- Đa hình theo ref_type nên ref_id cố ý KHÔNG phải FK (p4 §4.9.3 vòng lặp 2):
    -- mất ràng buộc tham chiếu, bù lại bằng index (ref_type, ref_id) bên dưới.
    ref_type        VARCHAR(32),
    ref_id          UUID,
    -- Chống double-submit: lớp bảo vệ thứ hai sau idempotency_record (bất biến I4).
    idempotency_key VARCHAR(64) UNIQUE,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_credit_ledger_type
        CHECK (type IN ('GRANT', 'CONSUME', 'EXPIRE', 'REFUND', 'ADJUST')),
    CONSTRAINT ck_credit_ledger_ref_type
        CHECK (ref_type IS NULL OR ref_type IN ('SCAN', 'ACTIVATION', 'JOB', 'ADMIN')),
    CONSTRAINT ck_credit_ledger_amount
        CHECK (amount <> 0),
    -- Ràng buộc dấu theo type — p4 group E "Ràng buộc DB bổ sung".
    CONSTRAINT ck_credit_ledger_sign CHECK (
        (type = 'GRANT'   AND amount > 0) OR
        (type = 'REFUND'  AND amount > 0) OR
        (type = 'CONSUME' AND amount < 0) OR
        (type = 'EXPIRE'  AND amount < 0) OR
        (type = 'ADJUST')
    ),
    -- ADJUST (admin điều chỉnh) bắt buộc có note + audit (p4 §4.4.6).
    CONSTRAINT ck_credit_ledger_adjust_note
        CHECK (type <> 'ADJUST' OR (note IS NOT NULL AND length(btrim(note)) > 0))
);

-- Màn lịch sử giao dịch, phân trang keyset theo (created_at, id) (p8 §8.1.4).
CREATE INDEX ix_credit_ledger_user_created
    ON credit_ledger (user_id, created_at DESC);
-- Kiểm bất biến I1/I3: tổng ledger của một lô luôn khớp remaining_amount của lô đó.
CREATE INDEX ix_credit_ledger_batch_created
    ON credit_ledger (batch_id, created_at);
-- Từ một scan_id tìm ngược dòng ledger để hoàn credit (p5 R7).
CREATE INDEX ix_credit_ledger_ref
    ON credit_ledger (ref_type, ref_id)
    WHERE ref_id IS NOT NULL;

-- Ép append-only bằng QUYỀN (p4 §4.6.2). V2__roles_and_grants.sql ở M0 mới tạo
-- `catcheck_app`; `catcheck_job` / `catcheck_readonly` / `catcheck_migrate` là 3 role còn
-- lại của p4 §4.6.1 mà V2 ghi TODO chưa tạo. Vì REVOKE FROM một role không tồn tại sẽ
-- làm Flyway fail, phần `catcheck_job` được bọc trong DO block có điều kiện pg_roles —
-- khi W3 bổ sung role, câu REVOKE này tự động có hiệu lực mà không cần sửa checksum.
REVOKE UPDATE, DELETE ON credit_ledger FROM catcheck_app;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_job') THEN
        EXECUTE 'REVOKE UPDATE, DELETE ON credit_ledger FROM catcheck_job';
    END IF;
END
$$;

-- p4 §4.6.1: catcheck_readonly (điều tra/BI) KHÔNG được thấy activation_code.code_hash —
-- người điều tra sự cố không cần và không được thấy bí mật xác thực. Cùng lý do với
-- email_otp.code_hash / password_reset.token_hash / user_mfa_totp.secret_enc (V5, V15).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_readonly') THEN
        EXECUTE 'GRANT SELECT ON activation_code TO catcheck_readonly';
        EXECUTE 'REVOKE SELECT (code_hash) ON activation_code FROM catcheck_readonly';
    END IF;
END
$$;

-- ============================================================================
-- 4. user_entitlement — bảng dẫn xuết: quyền tính năng hiện tại của user
-- ============================================================================
-- PK là chính user_id (p4 §4.1.1: quan hệ 1-1 bắt buộc với app_user, dùng user_id làm PK
-- khiến "một user một dòng" là bất biến của DB chứ không phải của code).
CREATE TABLE user_entitlement (
    user_id            UUID PRIMARY KEY REFERENCES app_user (id),
    -- Gói CAO NHẤT user từng kích hoạt (p5 R5). Cố ý không FK package_plan: entitlement
    -- không hết hạn cùng credit, nên giữ mã gói sau khi gói bị inactive cũng phải còn đọc được.
    highest_package    VARCHAR(32),
    -- NULL = không giới hạn (p5 §5.3). Áp dụng khi TẠO MỚI, không hồi tố (p5 R5, I27).
    max_cat_profiles   INT,
    features           JSONB       NOT NULL,
    -- Hết hạn gói còn hiệu lực MUỘN NHẤT — cột được job cập nhật, không tính động (bất biến I28).
    write_access_until TIMESTAMPTZ,
    trial_scans_used   INT         NOT NULL DEFAULT 0,
    -- Bảng dẫn xuất phải biết mình được tính lại lần cuối lúc nào, nếu không không phát
    -- hiện được lúc nó lệch (p4 group E, cột bổ sung).
    recomputed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- 5. Ghi chú bàn giao — những thứ CỐ Ý KHÔNG làm ở V10
-- ============================================================================
-- * `package_plan` (PK code, 5 gói) do V7 của module cat/content tạo — file này chỉ tham
--   chiếu, không seed. Seed 5 gói nằm ở db/seed/R__seed_package_plan.sql theo p4 §4.9.2.
-- * Trigger set_updated_at() cho cả 4 bảng có updated_at nằm ở V16 (W3), không tạo ở đây
--   để không tranh chủ quyền sở hữu trigger với file đó.
-- * RLS cho user_entitlement / credit_batch / credit_ledger / activation_code nằm ở V17 (W3)
--   theo p4 §4.6.3 (14 bảng thuộc sở hữu).
-- * scan.credit_ledger_id FK nằm ở V11 (module scan) theo p4 §4.9.2/§4.9.3.
