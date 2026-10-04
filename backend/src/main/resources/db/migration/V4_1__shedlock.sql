-- V4_1: Bảng shedlock của ShedLock 7.10.1 (net.javacrumbs.shedlock:shedlock-provider-jdbc-template).
--      (Flyway đọc tên này là version **4.1** — xem giải thích bên dưới.)
--
-- ⚠ VỊ TRÍ VÀ TÊN FILE — đọc trước khi đổi. Ba ràng buộc phải thoả cùng lúc:
--
--   1. p4 §4.9.2 đặt `shedlock` ở khe NGAY SAU V4 và TRƯỚC V5 (gom bảng framework lại với
--      SPRING_SESSION / event_publication, trước mọi schema nghiệp vụ). p4 là part sở hữu
--      miền migration (`context/spec/04-index.md` §2) nên thứ tự này được tôn trọng.
--   2. p4 §4.9.2 đã đặt trước **V16** cho `V16__triggers_and_invariants.sql` và **V17** cho
--      `V17__rls.sql`. Không được chiếm hai số đó.
--   3. Flyway phải thực sự nhận ra file.
--
-- Tên literal "V4b" mà p4 §4.9.2 viết KHÔNG thoả ràng buộc 3, và cái giá của nó không phải là
-- một lỗi ồn ào: Flyway **bỏ qua file trong im lặng** vì "4b" không phải version hợp lệ
-- (version chỉ gồm chữ số, phân tách bằng "." hoặc "_"). Đo thật trên PostgreSQL 18.6 +
-- Flyway 12.4.0 bằng `SchemaInvariantTests`:
--     V4b__shedlock.sql  -> áp dụng 23 migration, 60 bảng, KHÔNG có bảng `shedlock`
--     V4_1__shedlock.sql -> áp dụng 24 migration, 61 bảng, log "4.1 - shedlock" ngay sau "4"
-- Không có cảnh báo nào ở lần migrate đầu; hỏng chỉ lộ ra lúc ShedLock chạy job đầu tiên.
--
-- Đặt V17 cho shedlock cũng KHÔNG được: nó chiếm đúng ô mà §4.9.2 dành cho RLS (ràng buộc 2).
--
-- `V4_1` thoả cả ba: Flyway coi "_" là dấu phân tách version hợp lệ (tương đương "."), nên
-- version là **4.1**, xếp đúng khe giữa V4 và V5; V16/V17 vẫn nguyên vẹn cho trigger và RLS.
--
-- Hệ quả cần p7 sửa: regex R17 ở p7 §7.6.2 (`^V\d+__[a-z0-9_]+\.sql$`) không khớp cả "V4b"
-- lẫn "V4_1". Test `MigrationNamingTests` đã nới đúng một nhánh `(_\d+)?` theo p4; văn bản
-- p7 §7.6.2 cần cập nhật cho khớp. Xem handoff H15.33 / H15.65.
--
-- DDL bên dưới lấy đúng nguyên văn phần PostgreSQL trong README chính thức của dự án ShedLock
-- (https://github.com/lukas-krecan/ShedLock, mục "Configure JdbcTemplate lock provider"), đã
-- fetch trực tiếp để xác nhận đúng version 7.10.1 hiện hành.

CREATE TABLE shedlock
(
    name      VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP   NOT NULL,
    locked_at TIMESTAMP    NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
