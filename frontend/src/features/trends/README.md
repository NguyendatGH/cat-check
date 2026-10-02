# features/trends

- Làm gì: Biểu đồ xu hướng pH theo thời gian (dùng recharts, lazy-load) + phân bố phân loại trong kỳ. Tự dựng từ `GET /scans` + `GET /scans/summary` (E2/E3) thay vì `GET /cats/:catId/trends` (D13 — stub 501, chờ scan/insight tại thời điểm cat module viết controller).
- Route dùng: /cats/:catId/trends
- Entitlement: Không giới hạn ở M3 (mockup có nhắc "AI Pattern Insights" premium nhưng đó là tính năng AI ngoài phạm vi MVP — quyết định #3 — không triển khai ở đây, xem `docs/handovers/A6.md`).
- Trạng thái: M3 (A6) — business logic thật qua MSW mock (`mocks.ts`). Chưa nối API thật.
