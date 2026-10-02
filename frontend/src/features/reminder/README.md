# features/reminder

- Làm gì: Lịch nhắc quét màu cát định kỳ (nhóm I, p8 §8.4.9 — I1…I6).
- Route dùng: /reminders, /reminders/new, /reminders/:reminderId
- Entitlement: cần tính năng `reminder` trong gói — server trả 403 `FEATURE_NOT_IN_PLAN` nếu thiếu.
- Trạng thái: đã nối API thật (`GET|POST /reminders`, `GET|PATCH|DELETE /reminders/{id}`,
  `GET /reminders/{id}/calendar.ics`). Không còn mock.
- Lưu ý hợp đồng:
  - server bỏ hẳn field null (`default-property-inclusion: non_null`) ⇒ `nextRunAt` VẮNG MẶT
    khi lịch tắt, không phải `null`;
  - giờ ưu tiên GỬI `HH:mm` nhưng NHẬN `HH:mm:ss` (xem `apiTimeToInput`/`inputTimeToApi`);
  - PATCH dùng `application/merge-patch+json` — field vắng = không đổi;
  - kênh Phase 1 chỉ có `PUSH`/`EMAIL`/`IN_APP`.
