# features/cat

- Làm gì: CRUD hồ sơ mèo, ảnh đại diện, lưu trữ/mèo mặc định, ghi chú, khảo sát sức khoẻ,
  báo cáo dấu hiệu lâm sàng (p8 §8.4.4 nhóm D1-D20).
- Route dùng: /cats, /cats/new, /cats/:catId, /cats/:catId/edit
- Entitlement: `max_cat_profiles` (kiểm ở BE khi tạo, `CAT_PROFILE_LIMIT_REACHED`)
- Trạng thái: M2 — CRUD đầy đủ + notes + khảo sát + dấu hiệu lâm sàng (RHF+zod, react-query,
  MSW mocks). `CatController` HTTP thật CHƯA tồn tại ở backend (chỉ domain/application/DTO
  — xem `docs/handovers/A3.md`); FE dựng đúng theo hợp đồng `p8 §8.4.4`, W3/A6 nối API thật.

## Cấu trúc

Toàn bộ code nằm ở các file root (không thư mục con) — theo đúng khuôn mẫu bắt buộc của
`features/onboarding` (xem `docs/handovers/A7.md` §6 và `docs/handovers/A3-fe.md`).

- `types.ts` — type + hằng riêng của feature (domain type dùng chung nằm ở `entities/cat`)
- `schemas.ts` — zod schema (message = key i18n `cat`)
- `api.ts` — `apiFetch` + `apiUploadAvatar` (thay bằng `apiClient` khi có OpenAPI thật)
- `hooks.ts` — react-query hooks theo hợp đồng p8 §8.4.4, key factory `catKeys`
- `components.tsx` — component dùng chung (BreedPicker, AvatarUpload, ClinicalSignPicker...)
- `mocks.ts` — MSW handlers + worker (dev-only)
