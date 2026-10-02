# features/admin

- Làm gì: dữ liệu + mảnh UI dùng chung cho khu vực quản trị mức MVP (quyết định owner #14).
- Route dùng: /admin/**
- Entitlement: `RequireRole` ở router đã chặn; feature KHÔNG lặp lại kiểm tra vai trò.
- Trạng thái: đã nối API thật cho bảng màu pH (L27–L32, L36, L37), nội dung (L40–L45),
  `/admin/system-status`, và hai danh mục quyền riêng tư chỉ đọc (C5, C1).

## Quy ước

- `api.ts` — fetch wrapper cục bộ (ESLint `boundaries` cấm feature import feature).
  Trả cả header `ETag` vì L30/L31/L37 bắt buộc `If-Match`.
- `reason` bắt buộc ≥ 10 ký tự cho mọi hành động ghi (p17 §17.3.8 AD10/AD11); chi tiết
  vì sao vẫn gửi với endpoint chưa có trường này: xem khối chú thích đầu `api.ts`.
- Endpoint admin CHƯA có backend thì trang dùng `MissingApiNotice` nêu đích danh mã
  endpoint + mục spec sở hữu. KHÔNG mock, KHÔNG mượn endpoint user-facing thay thế.
