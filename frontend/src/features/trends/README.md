# features/trends

- Làm gì: Biểu đồ xu hướng pH theo thời gian (dùng recharts, lazy-load) + phân bố phân loại trong kỳ. NGUỒN DUY NHẤT là `GET /cats/:catId/trends` (D13) — endpoint thật. Đường tự dựng từ `GET /scans` + `GET /scans/summary` (E2/E3), viết khi D13 còn là stub 501, đã bị xoá ở W1-E vì nó cho số liệu lệch với màn Xu hướng desktop vốn đã gọi D13.
- Route dùng: /cats/:catId/trends
- Entitlement: Không giới hạn ở M3 (mockup có nhắc "AI Pattern Insights" premium nhưng đó là tính năng AI ngoài phạm vi MVP — quyết định #3 — không triển khai ở đây, xem `docs/handovers/A6.md`).
- Trạng thái: React Query calls the native `/api/v1/cats/{catId}/trends` endpoint; `mocks.ts` is a legacy fixture, not started in the application.
