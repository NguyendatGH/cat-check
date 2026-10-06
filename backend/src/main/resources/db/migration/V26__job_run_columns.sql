-- ============================================================================
-- V26 — job_run: bo sung 6 cot + trang thai SKIPPED_THRESHOLD con thieu so voi dac ta.
--
-- VI SAO CAN MIGRATION NAY (khong phai tien nghi):
-- p4 §K3 va p12 §12.8.2 dac ta `job_run` co `trigger_type`, `dry_run`,
-- `items_processed`, `items_deleted`, `items_failed`, `instance_id`, va
-- `status` nhan ca `SKIPPED_THRESHOLD`. `V15__ops.sql` tao bang HEP HON:
-- chi (id, job_name, status, started_at, finished_at, duration_ms, row_count,
-- error_summary) — ghi nhan o handoff H15.44.
--
-- Hau qua THAT, khong phai ly thuyet: p8 L65 `POST /admin/jobs/{jobName}/run`
-- duoc dac ta la "kich hoat thu cong mot job (trigger_type = MANUAL)", va p14
-- o Q26 doi man "Log job nen" tra loi duoc cau "job nay tu chay theo lich hay
-- co nguoi bam?". Khong co cot `trigger_type` that thi L65 khong the lam dung
-- viec cua no — `JdbcJobRunAdapter` dang phai nhoi chuoi "trigger=MANUAL" vao
-- `error_summary`, tuc la mot lan chay THANH CONG do admin bam lai hien ra o
-- UI nhu mot lan chay CO LOI. Day la ngoai le migration duy nhat cua dot W5-D.
--
-- Them cot co DEFAULT nen `ALTER TABLE` khong rewrite bang (PostgreSQL >= 11
-- luu default vao catalog) — an toan tren bang dang co san dong.
-- ============================================================================

ALTER TABLE job_run
    ADD COLUMN IF NOT EXISTS trigger_type    VARCHAR(16) NOT NULL DEFAULT 'SCHEDULE',
    ADD COLUMN IF NOT EXISTS dry_run         BOOLEAN     NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS items_processed INT,
    ADD COLUMN IF NOT EXISTS items_deleted   INT,
    ADD COLUMN IF NOT EXISTS items_failed    INT,
    ADD COLUMN IF NOT EXISTS instance_id     VARCHAR(64);

COMMENT ON COLUMN job_run.trigger_type IS
    'SCHEDULE | MANUAL | EVENT. MANUAL = admin bam chay lai qua p8 L65 (p4 §K3).';
COMMENT ON COLUMN job_run.dry_run IS
    'p15 REQ-RET-01: lan chay chi dem van ghi mot dong, de nguoi van hanh biet minh da thu gi.';
COMMENT ON COLUMN job_run.items_deleted IS
    'Tach khoi items_processed: "xet 10 000, xoa 3" va "xet 10 000, xoa 10 000" la hai tinh huong khac han (p4 §K3).';
COMMENT ON COLUMN job_run.instance_id IS
    'Hostname/container id — debug khi chay nhieu instance. KHONG chua PII.';

-- Doi du lieu cu sang cot moi. `row_count` cua V15 chinh la `items_processed`
-- cua dac ta; giu nguyen cot cu (L64 va JdbcOpsQueryAdapter dang doc no) va
-- tu nay ghi CA HAI de khong co giai doan nao man admin bi trong so lieu.
UPDATE job_run SET items_processed = row_count WHERE items_processed IS NULL AND row_count IS NOT NULL;

-- Phuc hoi trigger_type/dry_run tu cho xuong thang truoc day: adapter cu nhoi
-- "dry-run" va "trigger=MANUAL" vao `error_summary` (H15.44). Doc lai roi don
-- cot lo di, neu khong hai gia tri do mai nam o cot danh cho THONG BAO LOI.
UPDATE job_run SET trigger_type = 'MANUAL' WHERE error_summary LIKE '%trigger=MANUAL%';
UPDATE job_run SET trigger_type = 'EVENT'  WHERE error_summary LIKE '%trigger=EVENT%';
UPDATE job_run SET dry_run = true          WHERE error_summary LIKE 'dry-run%';
UPDATE job_run
   SET error_summary = NULLIF(btrim(regexp_replace(
           regexp_replace(error_summary, '(^|; )(dry-run)( trigger=[A-Z]+)?;? ?', '\1', 'g'),
           'trigger=[A-Z]+;? ?', '', 'g')), '')
 WHERE error_summary LIKE 'dry-run%' OR error_summary LIKE '%trigger=%';

-- ============================================================================
-- LOI DAC TA DO DUOC, KHONG PHAI SUY DOAN: `status` VARCHAR(16) KHONG CHUA NOI
-- GIA TRI MA CHINH DAC TA DOI.
--
-- p4 §K3 va p12 §12.8.2 deu ghi `status VARCHAR(16)` VA deu liet
-- 'SKIPPED_THRESHOLD' la mot gia tri hop le. Nhung chuoi do dai 17 ky tu
-- (SKIPPED = 7, '_' = 1, THRESHOLD = 9), nen moi lan ghi no PostgreSQL nem
-- `value too long for type character varying(16)`.
--
-- Do thuc te bang JdbcJobRunAdapterSchemaTest tren postgres:18.6-trixie that:
-- `UPDATE job_run SET status = 'SKIPPED_THRESHOLD'` that bai o dong 16 ky tu.
-- Bug nay truoc day BI CHE vi adapter dang xuong thang, ghi de
-- 'SKIPPED' (7 ky tu) — tuc la no chi lo ra dung luc sua cho xuong thang.
--
-- Hau qua that neu khong sua: `CleanupDeadPushTokensJob` cham nguong an toan
-- 20% (p15 REQ-RET-02) se khong dong duoc dong `job_run` — dong ket
-- `status='RUNNING'` + `finished_at` NULL vinh vien, va `JobHeartbeatCheckJob`
-- tuong job van song. Mot lan chan xoa bat thuong — dung thu p15 can nhin
-- thay nhat — tro thanh vo hinh.
--
-- Noi rong len 24: du cho 'SKIPPED_THRESHOLD' va con cho cho mot trang thai
-- dai hon sau nay. Ghi handoff H15.180 de p4/p12 sua kieu cot trong van ban.
-- ============================================================================
ALTER TABLE job_run ALTER COLUMN status TYPE VARCHAR(24);

-- `ck_job_run_status` cua V15 khong co SKIPPED_THRESHOLD nen adapter phai ghi
-- de thanh 'SKIPPED'. p4 §K3 noi ro vi sao phai phan biet: job dung vi cham
-- nguong an toan 20% (p15 REQ-RET-02) la he thong HOAT DONG DUNG, gop vao
-- FAILED se khien nguoi truc bo qua no dung luc can chu y nhat.
-- Giu them 'SKIPPED' trong CHECK de cac dong cu khong vi pham rang buoc.
ALTER TABLE job_run DROP CONSTRAINT IF EXISTS ck_job_run_status;
ALTER TABLE job_run
    ADD CONSTRAINT ck_job_run_status CHECK (status IN (
        'RUNNING', 'SUCCESS', 'FAILED', 'PARTIAL', 'SKIPPED', 'SKIPPED_THRESHOLD'));

ALTER TABLE job_run DROP CONSTRAINT IF EXISTS ck_job_run_trigger_type;
ALTER TABLE job_run
    ADD CONSTRAINT ck_job_run_trigger_type CHECK (trigger_type IN ('SCHEDULE', 'MANUAL', 'EVENT'));

ALTER TABLE job_run DROP CONSTRAINT IF EXISTS ck_job_run_items;
ALTER TABLE job_run
    ADD CONSTRAINT ck_job_run_items CHECK (
        (items_processed IS NULL OR items_processed >= 0)
        AND (items_deleted IS NULL OR items_deleted >= 0)
        AND (items_failed IS NULL OR items_failed >= 0));

-- p4 §K3 index thu hai: hai trang thai can nguoi xu ly, index chi chua dung chung.
DROP INDEX IF EXISTS idx_job_run_needs_attention;
CREATE INDEX idx_job_run_needs_attention ON job_run (started_at DESC)
    WHERE status IN ('FAILED', 'SKIPPED_THRESHOLD');

-- ============================================================================
-- H15.45 — `catcheck_app` chi co SELECT, INSERT tren `job_run` (V15 dong 171),
-- THIEU UPDATE. Nhung `JobRunRecorder.finish()` la mot cau UPDATE: moi lan
-- chay job se khong dong duoc dong, de lai status='RUNNING' + finished_at NULL
-- vinh vien va `JobHeartbeatCheckJob` tuong job con song. Local khong lo vi app
-- noi bang role chu so huu `catcheck`, nhung staging/prod theo p18 dung
-- `catcheck_app` — tuc la bug chi no o moi truong that.
--
-- UPDATE khac han audit_log: `job_run` la LOG VAN HANH, khong phai so audit
-- append-only (p4 §K3 ghi ro hai bang phuc vu hai muc dich). Mo dung UPDATE,
-- KHONG mo DELETE (retention do job don dep chay bang role khac).
-- ============================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'catcheck_app') THEN
        GRANT UPDATE ON job_run TO catcheck_app;
    END IF;
END $$;
