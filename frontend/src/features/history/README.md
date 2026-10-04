# features/history

- Làm gì: Timeline lịch sử scan theo mèo — lọc (tất cả/bất thường/tranh chấp), phổ pH tháng hiện tại (dùng E3 `/scans/summary`), lối vào export.
- Route dùng: /cats/:catId/history, /history (redirect)
- Entitlement: Không cần entitlement
- Trạng thái: React Query hooks call native `/api/v1/scans` list/summary and `/api/v1/cats/{id}` endpoints. `mocks.ts` is a legacy fixture, not started in the application.
- Ghi chú: Bỏ chip lọc "Ghi chú & Triệu chứng" của mockup `07` — `GET /scans` (E2) không có cột `hasNote` thật ở M3 (xem `docs/handovers/A6.md`).
