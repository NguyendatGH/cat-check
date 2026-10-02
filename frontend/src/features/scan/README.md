# features/scan

- Làm gì: Chụp ảnh cát, gửi phân tích pH (E1, một bước: phân tích + lưu + trừ credit), xem kết quả/chi tiết, gán lại mèo (E9), tranh chấp kết quả (E10/E11).
- Route dùng: /scan/select-cat, /scan, /scan/result/:scanId, /scan/:scanId/reassign-cat, /scans/:scanId
- Entitlement: Cần credit còn lại để chụp mới (server kiểm tra trước khi chạy pipeline — 402 nếu hết)
- Trạng thái: M3 (A6) — business logic thật qua MSW mock (`mocks.ts`), khớp hợp đồng p8 §8.5.4 nhóm E. Chưa nối API thật (chờ W3 thay `apiFetch` bằng `apiClient` + OpenAPI type).
- Ghi chú: `/shared-tray-log` liệt kê ở README gốc nhưng KHÔNG có trang nào implement trong đợt này (ngoài phạm vi A6, xem `docs/handovers/A6.md`).
