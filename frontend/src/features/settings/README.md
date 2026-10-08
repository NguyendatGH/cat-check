# features/settings

- Làm gì: tuỳ chọn thông báo của tài khoản (B11/B12 `GET|PUT /account/notification-preferences`).
- Route dùng: /settings (khối "Thông báo và cảnh báo"), /settings/notifications — qua `pages/settings/NotificationPreferencesForm.tsx`.
- Entitlement: Không cần entitlement
- Trạng thái: đã nối API thật. Hồ sơ/bảo mật/ngôn ngữ dùng hook của `features/auth`; quyền riêng tư dùng `features/privacy`.
