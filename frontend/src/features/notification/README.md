# features/notification

- Làm gì: Hộp thư thông báo trong app + đăng ký thiết bị nhận push FCM (dùng firebase/*, chỉ ở đây và src/sw.ts).
- Route dùng: /notifications, /settings/notifications
- Entitlement: Không cần entitlement
- Trạng thái: **đã nối API thật** — 8 endpoint nhóm G của p8 §8.4.7:
  - G4 `GET /notifications` (cursor) · G5 `GET /notifications/unread-count` · G6 `POST /notifications/{id}/read`
    · G7 `POST /notifications/read-all` · G8 `DELETE /notifications/{id}`
  - G9 `GET /push/subscriptions` · G10 `PUT /push/subscriptions` · G11 `DELETE /push/subscriptions/{id}`
- File: `api.ts` (fetch wrapper cục bộ) · `types.ts` · `hooks.ts` (react-query) · `components.tsx`
  (`NotificationUnreadBadge`, `PushDevicesCard`) · `pushClient.ts` (lớp trình duyệt + Firebase SDK).
- Push CHỈ bật khi đủ `VITE_FIREBASE_VAPID_KEY` **và** 4 khoá cấu hình Firebase Web App
  (`shared/config/env.ts`). Thiếu bất kỳ khoá nào ⇒ `pushAvailability()` trả lý do và UI hiện lời
  giải thích thay cho công tắc — không có nút chết.
