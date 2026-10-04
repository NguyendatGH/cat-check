# features/onboarding

- Làm gì: Luồng thiết lập ban đầu sau đăng ký: mèo đầu tiên, khảo sát sức khoẻ, disclaimer, kích hoạt.
- Route dùng: /onboarding/cat, /onboarding/health-survey, /onboarding/disclaimer, /onboarding/activate, /onboarding/success
- Entitlement: Không cần entitlement
- Trạng thái: M2 — 5 bước hoàn chỉnh (RHF+zod, zustand, react-query, MSW mocks).

## Cấu trúc

Toàn bộ code nằm ở các file root (không thư mục con) vì `eslint.config.js` thiếu policy
`feature → feature` — import giữa các file cùng feature bị rule `boundaries/dependencies`
chặn. W3 bổ sung policy thì tách `components.tsx` → `components/`, `mocks.ts` → `mocks/`.
Xem `docs/handovers/A7.md` §6.

- `types.ts` — type + hằng
- `schemas.ts` — zod schema + validate mã kích hoạt (message = key i18n)
- `store.ts` — zustand (step, catDraft, createdCat, surveyAnswers, activation)
- `api.ts` — `apiFetch` + `apiUploadAvatar` (thay bằng apiClient khi có OpenAPI)
- `hooks.ts` — 9 react-query hooks theo hợp đồng p8
- `components.tsx` — 10 component dùng chung
- `mocks.ts` — MSW handlers + worker (dev-only)
