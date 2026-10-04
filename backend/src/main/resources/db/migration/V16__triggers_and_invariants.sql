-- V16: Trigger + bất biến ép ở tầng DB.
--
-- Nguồn đặc tả: spec/parts/p4-domain-model-erd.md §4.9.2 (dòng V16) — "Trigger `set_updated_at`
-- cho mọi bảng có `updated_at`; trigger bất biến I7 (`scan_image` chỉ tồn tại khi
-- `scan.store_image`); `EXCLUDE USING gist` cho I9; `CHECK` bất biến I31 trên `care_tip`;
-- stored procedure `SECURITY DEFINER` phục vụ ẩn danh hoá (§4.6.2)". Phụ thuộc: tất cả.
--
-- Phát biểu đầy đủ của từng bất biến ở p4 §4.5.1; quy trình ẩn danh hoá ở p4 §4.6.5(c).
--
-- ⚠ BA CHỖ LỆCH KHỎI CÂU LỆNH SQL VIẾT NGUYÊN VĂN TRONG SPEC — đều là lệch BẮT BUỘC vì câu
--   lệnh nguyên văn không chạy được hoặc không ép được đúng điều mà chính spec phát biểu.
--   Từng chỗ được giải thích tại chỗ bên dưới và ghi handoff H15:
--     (a) I9: dùng `min_inclusive`/`max_inclusive` thay cho hằng `'[]'`, và loại dải không có
--         biên pH ra khỏi vị từ partial.
--     (b) I31: bọc `coalesce(..., 0)` vì `length(btrim(NULL))` là NULL ⇒ CHECK "unknown" ⇒ ĐẬU.
--     (c) Thủ tục ẩn danh hoá KHÔNG đổi được 3 cột có khoá ngoại tới `app_user(id)` sang
--         `pseudonym_id` — §4.6.5(c) bước 7/9 bất khả thi về mặt FK; xem khối giải thích dài
--         ở mục 5 (có kèm thông điệp lỗi thật khi chạy trên PostgreSQL 18.6).

-- ===========================================================================
-- 1. Trigger `set_updated_at` cho MỌI bảng có cột `updated_at` (p4 §4.9.2, §4.9.4 mục 4).
--
-- Viết dạng vòng lặp động thay vì liệt kê tay 29 `CREATE TRIGGER`: danh sách bảng là một
-- truy vấn trên catalog nên KHÔNG THỂ bỏ sót bảng nào đã tồn tại tại thời điểm V16 chạy —
-- đúng mệnh đề "mọi bảng có updated_at". Bảng thêm SAU V16 phải tự tạo trigger trong chính
-- migration tạo bảng đó (và test `SchemaInvariantTests` là lưới bắt nếu quên).
-- Hàm `set_updated_at()` được tạo ở V1.
-- ===========================================================================
DO
$$
    DECLARE
        r            record;
        trigger_name text;
    BEGIN
        FOR r IN
            SELECT c.table_name
            FROM information_schema.columns c
                     JOIN information_schema.tables t
                          ON t.table_schema = c.table_schema AND t.table_name = c.table_name
            WHERE c.table_schema = 'public'
              AND c.column_name = 'updated_at'
              AND t.table_type = 'BASE TABLE'
            ORDER BY c.table_name
            LOOP
                trigger_name := 'trg_' || r.table_name || '_updated_at';
                EXECUTE format('DROP TRIGGER IF EXISTS %I ON public.%I', trigger_name, r.table_name);
                EXECUTE format(
                        'CREATE TRIGGER %I BEFORE UPDATE ON public.%I '
                            || 'FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
                        trigger_name, r.table_name);
            END LOOP;
    END
$$;

-- ===========================================================================
-- 2. Bất biến I7 — `scan.store_image = false` ⇒ KHÔNG tồn tại dòng `scan_image` nào của scan đó.
--
-- Vế thứ hai của I7 (`is_trial = true ⇒ store_image = false`) đã được ép bằng
-- `ck_scan_trial_no_image` ở V11, không lặp lại ở đây.
--
-- p4 §4.5.1 I7: "trigger BEFORE INSERT ON scan_image raise exception nếu
-- (SELECT store_image FROM scan WHERE id = NEW.scan_id) = false. Phải ép ở DB: đây là cam kết
-- pháp lý với user (quyết định #12 + consent SCAN_IMAGE_RETAIN), và consent rút được bất cứ
-- lúc nào nên không thể chỉ tin vào nhánh `if` ở tầng ứng dụng".
--
-- Thông điệp lỗi chỉ chứa `scan_id` (UUID) — KHÔNG log PII (CLAUDE.md, p4 I29).
-- ===========================================================================
CREATE OR REPLACE FUNCTION enforce_scan_image_requires_store_image()
    RETURNS trigger
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_store_image boolean;
BEGIN
    SELECT s.store_image INTO v_store_image FROM scan s WHERE s.id = NEW.scan_id;

    IF v_store_image IS NULL THEN
        -- Không có scan cha: FK sẽ bắt ngay sau đó, nhưng chặn sớm để thông điệp rõ nghĩa.
        RAISE EXCEPTION 'I7: khong tim thay scan % cho scan_image', NEW.scan_id
            USING ERRCODE = '23514';
    END IF;

    IF NOT v_store_image THEN
        RAISE EXCEPTION 'I7: scan % co store_image = false nen khong duoc tao scan_image', NEW.scan_id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END
$$;

COMMENT ON FUNCTION enforce_scan_image_requires_store_image() IS
    'Bat bien I7 (p4 §4.5.1): chan INSERT scan_image khi scan.store_image = false.';

DROP TRIGGER IF EXISTS trg_scan_image_i7_store_image ON scan_image;
CREATE TRIGGER trg_scan_image_i7_store_image
    BEFORE INSERT
    ON scan_image
    FOR EACH ROW
EXECUTE FUNCTION enforce_scan_image_requires_store_image();

-- ===========================================================================
-- 3. Bất biến I9 — các `ph_classification_band` đang ACTIVE (cùng `chart_id`) KHÔNG chồng lấn.
--
-- p4 §4.5.1 I9 viết nguyên văn:
--   EXCLUDE USING gist (coalesce(chart_id, uuid_nil()) WITH =,
--                       numrange(min_ph, max_ph, '[]') WITH &&) WHERE (active)
--
-- (a) `uuid_nil()` thuộc extension `uuid-ossp`, mà V1 CHỈ cài `citext`, `pgcrypto`,
--     `btree_gist` (đúng theo p4 §4.9.2 dòng V1). Dùng hằng UUID zero thay thế — cùng ngữ
--     nghĩa "khoá gộp cho nhóm chart_id IS NULL", và là biểu thức IMMUTABLE nên index hoá được.
-- (b) Hằng `'[]'` (hai biên đều đóng) KHÔNG dùng được: bảng `ph_classification_band` có sẵn
--     hai cột `min_inclusive`/`max_inclusive` và 6 dải toàn cục seed ở V9 dùng biên nửa mở
--     (LOW < 6.0, SLIGHTLY_LOW [6.0, 6.3), IN_RANGE [6.3, 6.6], SLIGHTLY_HIGH (6.6, 7.0],
--     HIGH > 7.0). Với `'[]'` thì mọi cặp dải liền kề đều "chồng lấn" tại đúng điểm biên và
--     migration này sẽ ĐỎ ngay trên dữ liệu seed hợp lệ. Dựng kiểu biên từ chính hai cột cờ là
--     cách ép đúng MỆNH ĐỀ của I9 ("không chồng lấn") thay vì đúng chữ của một ví dụ SQL.
-- (c) Vị từ partial thêm điều kiện "dải phải có ít nhất một biên": dải `INCONCLUSIVE`
--     (min_ph IS NULL AND max_ph IS NULL, xem V9) không mô tả khoảng pH nào — `numrange(NULL,
--     NULL, ...)` lại là khoảng VÔ HẠN nên nó sẽ chồng lấn với tất cả. Loại nó ra là điều kiện
--     để constraint phản ánh đúng ý "dải phân loại theo pH".
--
-- Phần "phủ kín trục pH" của I9 KHÔNG ép được bằng constraint (ràng buộc trên tập dòng) —
-- p4 §4.5.1 giao cho service lúc publish + job đối soát §4.5.2.
-- ===========================================================================
ALTER TABLE ph_classification_band
    ADD CONSTRAINT ex_ph_classification_band_i9_no_overlap
        EXCLUDE USING gist (
        coalesce(chart_id, '00000000-0000-0000-0000-000000000000'::uuid) WITH =,
        numrange(
                min_ph,
                max_ph,
                (CASE WHEN min_inclusive THEN '[' ELSE '(' END)
                    || (CASE WHEN max_inclusive THEN ']' ELSE ')' END)
        ) WITH &&
        ) WHERE (active AND (min_ph IS NOT NULL OR max_ph IS NOT NULL));

COMMENT ON CONSTRAINT ex_ph_classification_band_i9_no_overlap ON ph_classification_band IS
    'Bat bien I9 (p4 §4.5.1): hai dai ACTIVE cung chart_id khong duoc chong lan khoang pH.';

-- ===========================================================================
-- 4. Bất biến I31 — `care_tip` không bao giờ PUBLISHED khi có claim mà thiếu nguồn.
--
-- p4 §4.5.1 I31 viết nguyên văn:
--   CHECK (status <> 'PUBLISHED' OR claim_type = 'NONE' OR length(btrim(source_reference)) >= 10)
--
-- Lệch bắt buộc: `source_reference` NULL được (V7 cho phép), và `length(btrim(NULL))` trả NULL
-- ⇒ toàn bộ CHECK về UNKNOWN ⇒ PostgreSQL coi là ĐẬU. Nghĩa là câu nguyên văn KHÔNG chặn được
-- đúng trường hợp nguy hiểm nhất mà I31 mô tả ("source_reference rỗng"). Bọc `coalesce(..., 0)`
-- — đúng cách mà chính p4 §4.5.2 viết truy vấn đối soát cho I31.
-- ===========================================================================
ALTER TABLE care_tip
    ADD CONSTRAINT ck_care_tip_i31_published_claim_needs_source
        CHECK (status <> 'PUBLISHED'
            OR claim_type = 'NONE'
            OR coalesce(length(btrim(source_reference)), 0) >= 10);

COMMENT ON CONSTRAINT ck_care_tip_i31_published_claim_needs_source ON care_tip IS
    'Bat bien I31 (p4 §4.5.1): PUBLISHED + claim_type <> NONE thi source_reference phai >= 10 ky tu.';

-- ===========================================================================
-- 5. Stored procedure `SECURITY DEFINER` phục vụ ẩn danh hoá (p4 §4.6.2 + §4.6.5(c)).
--
-- p4 §4.6.2: "Ngoại lệ duy nhất: quy trình xoá tài khoản (4.6.5) cần đổi
-- `user_id → pseudonym_id` trên `credit_ledger`, `audit_log`, `consent_record`,
-- `dsar_request`. Việc này chạy bằng MỘT stored procedure `SECURITY DEFINER` thuộc sở hữu của
-- `catcheck_migrate`, được `GRANT EXECUTE` cho `catcheck_job` — nghĩa là con đường duy nhất để
-- chạm vào các bảng đó là một hàm có tên, có audit, không phải một câu `UPDATE` bất kỳ."
--
-- ⚠⚠ PHÁT HIỆN KHI CHẠY THẬT — SPEC TỰ MÂU THUẪN, KHÔNG THỂ LÀM ĐÚNG CHỮ. Ghi handoff H15.
--
-- `pseudonym_id` KHÔNG phải một dòng `app_user` mới: p4 §4.6.5(c) bước 1 ghi nó vào
-- `app_user.pseudonym_id` của CHÍNH dòng cũ, và bước 11 giữ dòng `app_user` lại (ẩn danh hoá
-- tại chỗ) với lý do viết thẳng trong spec: "Không xoá dòng vì bước 6–9 còn FK trỏ tới".
-- Hệ quả: mọi cột có KHOÁ NGOẠI tới `app_user(id)` KHÔNG thể đổi sang `pseudonym_id` — không
-- có dòng `app_user` nào mang id đó. Đã xác nhận bằng cách chạy thật trên PostgreSQL 18.6:
--   ERROR: insert or update on table "audit_log" violates foreign key constraint
--          "audit_log_subject_user_id_fkey" — Key (subject_user_id)=(…) is not present in app_user
--
-- Ba cột ở bước 7/9 có FK tới `app_user(id)` (V6, V15) nên bị chặn:
--   `consent_record.user_id` (RESTRICT) · `policy_acknowledgment.user_id` (RESTRICT) ·
--   `audit_log.subject_user_id` (SET NULL)
-- Hai cột KHÔNG có FK nên đổi được, và hàm đổi đúng chúng:
--   `credit_ledger.user_id` (p4 §4.9.3 cố ý không đặt FK) · `audit_log.actor_id`
-- `dsar_request` đổi được vì bước 8 đưa `user_id` về NULL (FK cho phép NULL).
--
-- Cách giải quyết mà hàm này áp dụng — và lý do nó vẫn đạt mục tiêu pháp lý: sau bước 11, dòng
-- `app_user` KHÔNG CÒN TRƯỜNG NÀO xác định được con người (email → deleted-{pseudonym}@…,
-- full_name → "Người dùng đã xoá", phone/password_hash/avatar → NULL). Ba cột FK ở trên vì vậy
-- trỏ tới một dòng đã ẩn danh, và `app_user.pseudonym_id` là mối nối tới bút danh. Tức là ẩn
-- danh hoá vẫn đạt; chỉ cách hiện thực khác chữ của §4.6.5(c).
--
-- CẦN p4 CHỐT (handoff H15): hoặc (a) bỏ ba FK đó để đổi được sang `pseudonym_id` thật, hoặc
-- (b) sửa §4.6.5(c) bước 7/9 thành "giữ FK, ẩn danh hoá qua `app_user`" như hàm này làm.
-- KHÔNG tự bỏ FK ở V16: FK là do p4 §B2/§H2 đặc tả, gỡ nó là quyết định về schema.
--
-- Nội dung hàm (bám p4 §4.6.5(c)):
--   bước 6 `credit_ledger` : user_id -> pseudonym_id, xoá `note` (văn bản tự do, có thể chứa PII)
--   bước 8 `dsar_request`  : user_id -> NULL, set `pseudonym_id`,
--                            contact_email -> deleted-{pseudonym}@catcheck.invalid
--   bước 9 `audit_log`     : actor_id -> pseudonym_id
--
-- Hàm KHÔNG đụng `app_user` (bước 11), KHÔNG xoá dòng nào, và KHÔNG đụng `scan_reassignment`:
-- những việc đó `catcheck_app`/`catcheck_job` làm trực tiếp được, trừ một chỗ p4 §4.6.2 còn
-- thiếu — xem handoff H15 về việc `ConsentArchiveJob`/`DsarArchiveJob`/`AuditLogArchiveJob` và
-- bước 4 của §4.6.5(c) cần một đường XOÁ đặc quyền trên bảng append-only.
-- ===========================================================================
CREATE OR REPLACE FUNCTION anonymize_user_append_only(p_user_id uuid, p_pseudonym_id uuid)
    RETURNS TABLE
            (
                target_column text,
                rows_changed  bigint
            )
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = public, pg_temp
AS
$fn$
DECLARE
    v_count bigint;
BEGIN
    IF p_user_id IS NULL OR p_pseudonym_id IS NULL THEN
        RAISE EXCEPTION 'anonymize_user_append_only: p_user_id va p_pseudonym_id deu bat buoc'
            USING ERRCODE = '22023';
    END IF;
    IF p_user_id = p_pseudonym_id THEN
        RAISE EXCEPTION 'anonymize_user_append_only: pseudonym_id phai khac user_id'
            USING ERRCODE = '22023';
    END IF;

    -- Bước 6 (p4 §4.6.5(c)): giữ chứng từ đối soát, bỏ mọi móc nối tới con người.
    UPDATE credit_ledger
    SET user_id = p_pseudonym_id,
        note    = NULL
    WHERE user_id = p_user_id;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    target_column := 'credit_ledger.user_id';
    rows_changed := v_count;
    RETURN NEXT;

    -- Bước 8: giữ dạng ẩn danh 05 năm. `contact_email` chỉ được đổi SAU khi email xác nhận
    -- hoàn tất đã gửi — thứ tự đó do `ErasureExecutionJob` bảo đảm (p12 §12.6.5).
    UPDATE dsar_request
    SET user_id       = NULL,
        pseudonym_id  = p_pseudonym_id,
        contact_email = ('deleted-' || p_pseudonym_id::text || '@catcheck.invalid')::citext
    WHERE user_id = p_user_id;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    target_column := 'dsar_request.user_id';
    rows_changed := v_count;
    RETURN NEXT;

    -- Bước 9, phần đổi được: `audit_log.actor_id` không có FK tới app_user.
    UPDATE audit_log SET actor_id = p_pseudonym_id WHERE actor_id = p_user_id;
    GET DIAGNOSTICS v_count = ROW_COUNT;
    target_column := 'audit_log.actor_id';
    rows_changed := v_count;
    RETURN NEXT;

    -- Ba cột dưới đây BỊ FK CHẶN (xem khối giải thích đầu mục 5). Báo cáo tường minh với
    -- rows_changed = 0 thay vì im lặng, để log của job không trông như "đã xử lý xong".
    target_column := 'consent_record.user_id (FK giu nguyen - an danh hoa qua app_user)';
    rows_changed := 0;
    RETURN NEXT;

    target_column := 'policy_acknowledgment.user_id (FK giu nguyen - an danh hoa qua app_user)';
    rows_changed := 0;
    RETURN NEXT;

    target_column := 'audit_log.subject_user_id (FK giu nguyen - an danh hoa qua app_user)';
    rows_changed := 0;
    RETURN NEXT;

    RETURN;
END
$fn$;

COMMENT ON FUNCTION anonymize_user_append_only(uuid, uuid) IS
    'p4 §4.6.2 + §4.6.5(c): duong DUY NHAT doi user_id -> pseudonym_id tren cac cot append-only '
        'KHONG co FK toi app_user. SECURITY DEFINER, chi catcheck_job duoc EXECUTE.';

-- `SECURITY DEFINER` + quyền EXECUTE mặc định của PUBLIC = ai cũng chạy được hàm đặc quyền.
-- Thu hồi trước, cấp lại đúng một vai trò.
REVOKE ALL ON FUNCTION anonymize_user_append_only(uuid, uuid) FROM PUBLIC;

-- `catcheck_migrate` / `catcheck_job` chưa tồn tại: V2 mới chỉ tạo `catcheck_app` và tự ghi
-- TODO "bổ sung 3 role DB còn lại" (p4 §4.6.1). V16 KHÔNG tạo role thay V2 — đó là phạm vi của
-- một migration role riêng. Hai câu dưới có điều kiện để migrate không đỏ, và tự có hiệu lực
-- ngay khi role được thêm. Ghi handoff H15.
DO
$$
    BEGIN
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_migrate') THEN
            EXECUTE 'ALTER FUNCTION anonymize_user_append_only(uuid, uuid) OWNER TO catcheck_migrate';
        END IF;
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_job') THEN
            EXECUTE 'GRANT EXECUTE ON FUNCTION anonymize_user_append_only(uuid, uuid) TO catcheck_job';
        END IF;
    END
$$;
