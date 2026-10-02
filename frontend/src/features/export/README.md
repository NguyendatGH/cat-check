# features/export

- Làm gì: Wizard 3 bước (chọn mèo → khoảng thời gian → mục nội dung) tạo job PDF nền, poll trạng thái, tải file (p8 §8.4.10 nhóm J).
- Route dùng: /export, /export/:jobId
- Entitlement: Cần ít nhất 1 lần quét trong khoảng đã chọn (BE trả 422 `EXPORT_NO_DATA` nếu rỗng); tối đa 1 job đang chạy/user (409 `EXPORT_JOB_IN_PROGRESS`)
- Trạng thái: M3 (A6) — business logic thật qua MSW mock (`mocks.ts`, mô phỏng `QUEUED → RUNNING → READY` qua polling), khớp hợp đồng p8 §8.4.10. Chưa nối API thật.
- Ghi chú: Bỏ CTA "Gửi Email phòng khám" / "In tóm tắt" của mockup `10` — không có endpoint tương ứng trong p8 (chỉ có tạo/liệt kê/xem/tải, J1–J4); `VetClinic` chưa tồn tại trong MVP (xem `docs/handovers/A6.md`).
