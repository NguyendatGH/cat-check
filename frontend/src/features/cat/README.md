# features/cat

- Làm gì: CRUD hồ sơ mèo, ảnh đại diện, lưu trữ/mèo mặc định, ghi chú, khảo sát sức khoẻ,
  báo cáo dấu hiệu lâm sàng (p8 §8.4.4 nhóm D1-D20).
- Route dùng: /cats, /cats/new, /cats/:catId, /cats/:catId/edit
- Entitlement: `max_cat_profiles` (kiểm ở BE khi tạo, `CAT_PROFILE_LIMIT_REACHED`)
- Trạng thái: Frontend dùng API native qua `apiFetch`/`apiUploadAvatar`, backed by
  `CatController` and related controllers. Includes CRUD, notes, survey, clinical-sign reports,
  avatar, archive/restore and primary-cat actions. `mocks.ts` is a legacy fixture and is not
  started by the application. Native E2E covers create → soft-delete; other mutation paths need
  their own end-to-end coverage.

## Cấu trúc

Toàn bộ code nằm ở các file root (không thư mục con) — theo đúng khuôn mẫu bắt buộc của
`features/onboarding` (xem `docs/handovers/A7.md` §6 và `docs/handovers/A3-fe.md`).

- `types.ts` — type + hằng riêng của feature (domain type dùng chung nằm ở `entities/cat`)
- `schemas.ts` — zod schema (message = key i18n `cat`)
- `api.ts` — native API wrapper (`apiFetch`, multipart avatar upload)
- `hooks.ts` — react-query hooks theo hợp đồng p8 §8.4.4, key factory `catKeys`
- `components.tsx` — component dùng chung (BreedPicker, AvatarUpload, ClinicalSignPicker...)
- `mocks.ts` — MSW handlers + worker (dev-only)
