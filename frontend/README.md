# CatCheck — Frontend

React + TypeScript + Vite PWA cho CatCheck (theo dõi sức khoẻ mèo qua cát vệ sinh đổi màu theo pH).

**Trạng thái: M0 — khung nền tảng.** Router đăng ký đủ 80 route (68 Phase 1, 7 Phase 2, 5 Phase 3),
component/feature/entity đều là khung tối thiểu, chưa có business logic thật.

## Chạy dự án

```bash
npm install
npm run dev       # http://localhost:5173, proxy /api -> http://localhost:8080 (cấu hình trong vite.config.ts)
```

Backend Spring Boot cần chạy ở `localhost:8080` để proxy `/api` hoạt động (session cookie
HttpOnly + CSRF, same-origin qua proxy lúc dev).

## Script

```bash
npm run build       # tsc -b && vite build
npm run preview      # xem bản build
npm run typecheck    # tsc --noEmit
npm run lint         # eslint . --max-warnings=0
npm run format       # prettier --write .
npm run test         # vitest run
npm run e2e          # playwright test
npm run api:gen       # sinh lại src/shared/api/schema.d.ts từ OpenAPI backend (cần backend chạy)
```

## Ghi chú

- Node pin: `24.21.0` (xem `.nvmrc` + `engines.node`). Máy dev có thể dùng bản mới hơn, `npm install`/`npm run build` vẫn chạy được.
- i18n: nội dung dịch nguồn ở `src/shared/i18n/locales/`, được đồng bộ sang `public/locales/`
  lúc chạy vite (xem `syncLocalesToPublic` trong `vite.config.ts`) để `i18next-http-backend`
  fetch runtime — không sửa tay trong `public/locales/`.
- PWA: `vite-plugin-pwa` chiến lược `injectManifest`, service worker ở `src/sw.ts`. Push FCM
  còn để khung/TODO (chưa có Firebase project + VAPID key thật).
- Ranh giới kiến trúc (app/page/feature/entity/shared) enforce bằng `eslint-plugin-boundaries`
  trong `eslint.config.js` — xem comment trong file đó về việc dùng rule `boundaries/dependencies`
  hiện hành thay cho `boundaries/element-types`/`entry-point` đã deprecated.
