# features/export

- Làm gì: Wizard 3 bước (chọn mèo → khoảng thời gian → mục nội dung) tạo job PDF nền, poll trạng thái, tải file (p8 §8.4.10 nhóm J).
- Route dùng: /export, /export/:jobId
- Entitlement: Cần ít nhất 1 lần quét trong khoảng đã chọn (BE trả 422 `EXPORT_NO_DATA` nếu rỗng); tối đa 1 job đang chạy/user (409 `EXPORT_JOB_IN_PROGRESS`)
- Trạng thái: React Query calls native export job endpoints for create/list/status/download; status polling continues only while the backend reports `QUEUED`/`RUNNING`. `mocks.ts` is a legacy fixture, not started in the application.
- Ghi chú: Bỏ CTA "Gửi Email phòng khám" / "In tóm tắt" của mockup `10` — không có endpoint tương ứng trong p8 (chỉ có tạo/liệt kê/xem/tải, J1–J4); `VetClinic` chưa tồn tại trong MVP (xem `docs/handovers/A6.md`).
