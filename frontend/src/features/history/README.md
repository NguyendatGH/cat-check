# features/history

- Làm gì: Timeline lịch sử scan theo mèo — lọc (tất cả/bất thường/tranh chấp), phổ pH tháng hiện tại (dùng E3 `/scans/summary`), lối vào export.
- Route dùng: /cats/:catId/history, /history (redirect)
- Entitlement: Không cần entitlement
- Trạng thái: M3 (A6) — business logic thật qua MSW mock (`mocks.ts`), khớp hợp đồng p8 §8.5.4 E2/E3. Chưa nối API thật.
- Ghi chú: Bỏ chip lọc "Ghi chú & Triệu chứng" của mockup `07` — `GET /scans` (E2) không có cột `hasNote` thật ở M3 (xem `docs/handovers/A6.md`).
