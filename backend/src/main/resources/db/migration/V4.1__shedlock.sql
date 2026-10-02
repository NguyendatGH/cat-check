-- V4.1: Bảng shedlock của ShedLock 7.10.1 (net.javacrumbs.shedlock:shedlock-provider-jdbc-template).
--
-- ⚠ QUYẾT ĐỊNH KỸ THUẬT BẮT BUỘC — LỆCH KHỎI LITERAL STRING TRONG SPEC:
-- spec/parts/p4-domain-model-erd.md §4.9.2 gọi file này là "V4b__shedlock.sql". Flyway CHỈ chấp
-- nhận số ở phần version (regex ^V\d+(\.\d+)*__ — chữ cái như "4b" làm Flyway lỗi parse version
-- lúc khởi động ứng dụng, tức toàn bộ migration sẽ không chạy được). Dùng "V4.1" thay thế: Flyway
-- coi "." là dấu phân tách version hợp lệ (tương đương version số 4.1), sắp xếp đúng vị trí giữa
-- V4 và migration nghiệp vụ đầu tiên của M1 (sẽ là V5), giữ đúng Ý ĐỊNH thứ tự mà p4 §4.9.2 muốn
-- (bảng shedlock nằm ngay sau event_publication, trước schema nghiệp vụ). Đây là thay đổi bắt
-- buộc để migration thực sự chạy được, KHÔNG phải tự tiện đổi tên tuỳ tiện.
-- ĐỀ NGHỊ: owner cập nhật spec/parts/p4-domain-model-erd.md §4.9.2 (và ArchUnit rule R17 gốc ở
-- spec/parts/p7-backend-architecture.md §7.6.2 nếu rule gốc yêu cầu literal "V4b") để khớp với
-- quy ước "V4.1" này — xem báo cáo cuối nhiệm vụ.
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
