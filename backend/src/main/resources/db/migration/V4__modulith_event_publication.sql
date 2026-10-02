-- V4: Bảng event_publication của spring-modulith-events-jdbc (Spring Modulith BOM 2.1.1).
--
-- Nội dung cột lấy đúng theo yêu cầu nhiệm vụ (id, listener_id, event_type, serialized_event,
-- publication_date, completion_date) — đây chính là "legacy structure" (schema v1) của thư viện,
-- đã đối chiếu trực tiếp với file gốc
-- org/springframework/modulith/events/jdbc/schemas/v1/schema-postgresql.sql bên trong artifact
-- spring-modulith-events-jdbc:2.1.1 (giải nén jar tải từ Maven Central để xác nhận nguyên văn).
-- Thư viện còn có schema v2 mới hơn (thêm status, completion_attempts, last_resubmission_date)
-- nhưng KHÔNG nằm trong 6 cột được yêu cầu ở M0 — để ứng dụng dùng đúng cấu trúc bảng này lúc
-- runtime (thay vì cấu trúc v2 mặc định), BẮT BUỘC set
-- spring.modulith.events.jdbc.use-legacy-structure=true trong application.yml (đã cấu hình,
-- xem application.yml). Không set cờ này thì repository của thư viện sẽ query/insert vào các
-- cột v2 không tồn tại trong bảng bên dưới và lỗi lúc runtime.
--
-- Index: giữ index hash trên serialized_event giống bản gốc thư viện (dùng để dedupe/lookup),
-- nhưng đổi index trên completion_date thành PARTIAL INDEX (WHERE completion_date IS NULL)
-- theo đúng yêu cầu nhiệm vụ — tối ưu hơn bản gốc (chỉ đánh index các publication CHƯA hoàn
-- thành, vốn là tập nhỏ và là tập được quét thường xuyên nhất bởi cơ chế retry).

CREATE TABLE IF NOT EXISTS event_publication
(
    id               UUID NOT NULL,
    listener_id      TEXT NOT NULL,
    event_type       TEXT NOT NULL,
    serialized_event TEXT NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
    ON event_publication USING hash (serialized_event);

CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
    ON event_publication (completion_date)
    WHERE completion_date IS NULL;
