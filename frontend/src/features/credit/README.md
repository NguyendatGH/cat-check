# features/credit

- Làm gì: Quản lý credit trong tài khoản/settings — số dư, chi tiết từng lô (FEFO),
  lịch sử giao dịch, quyền tính năng (entitlement), kích hoạt thêm mã (p8 §8.4.8 H1-H4).
  Màn kích hoạt trong **onboarding** thuộc `features/onboarding` (A7) — feature này chỉ có
  màn kích hoạt độc lập `/credits/activate` (nạp thêm mã sau khi đã dùng app).
- Route dùng: /credits, /credits/activate
- Entitlement: Không cần entitlement để xem; nội dung hiển thị tuỳ theo entitlement hiện có.
- Trạng thái: M2 — số dư/ledger/entitlement/kích hoạt đầy đủ (react-query, MSW mocks).

## Cấu trúc

Toàn bộ code nằm ở các file root (không thư mục con) — theo đúng khuôn mẫu bắt buộc của
`features/onboarding` (xem `docs/handovers/A7.md` §6 và `docs/handovers/A4-fe.md`).

- `types.ts` — type khớp DTO thật `credit/api/dto/*.java`
- `schemas.ts` — validate định dạng mã kích hoạt (bản sao cục bộ của onboarding, không import chéo được)
- `api.ts` — `apiFetch` (thay bằng `apiClient` khi có OpenAPI thật)
- `hooks.ts` — react-query hooks theo hợp đồng p8 §8.4.8, key factory `creditKeys` + `entitlementKey`
- `components.tsx` — component dùng chung (CreditBalanceSummary, CreditBatchCard, LedgerEntryRow...)
- `mocks.ts` — MSW handlers + worker (dev-only)
