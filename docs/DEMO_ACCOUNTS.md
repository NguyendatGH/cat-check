# Tài khoản và mã credit demo

> **Chỉ dùng cho profile `local` — KHÔNG dùng cho production/staging.**
> Dữ liệu dưới đây được gieo tự động khi backend chạy với `SPRING_PROFILES_ACTIVE=local`
> (mặc định của `backend/run-local.sh`). Ở profile khác, các seeder không chạy.

## Tài khoản

Seeder: `backend/src/main/java/com/catcheck/identity/infrastructure/demo/DemoAccountSeeder.java`.
Mỗi lần khởi động, mật khẩu và vai trò được đặt lại về giá trị bên dưới.

| Email | Mật khẩu | Vai trò | Dùng để |
|---|---|---|---|
| `demo@catcheck.vn` | `Admin@123` | `USER` | Chủ nuôi: quét, hồ sơ mèo, nạp credit |
| `seller@catcheck.vn` | `Admin@123` | `USER`, `ADMIN_CATALOG` | Console seller/catalog |
| `admin@catcheck.vn` | `Admin@123` | `USER`, `ADMIN_SUPER` | Console admin (gồm mã kích hoạt) |

## Quà chào mừng (tài khoản mới)

Mọi tài khoản **người mua** mới tự nhận một lô quà **10 credit, hạn 5 ngày** — áp dụng mọi profile,
không chỉ `local`:

- Đăng ký email: tặng lúc xác minh OTP xong. Google OAuth: tặng lúc tạo tài khoản lần đầu.
- Đúng **một lần** mỗi tài khoản (khoá `credit_ledger.idempotency_key = welcome-bonus:<userId>`).
  Staff (có vai trò ngoài `USER`) không nhận. Ba tài khoản demo ở trên do seeder tạo thẳng nên
  **không** có quà — muốn thử, đăng ký tài khoản mới.
- Lô có `package_code = WELCOME`, hiện là "Quà chào mừng" ở màn Credit; chỉ cho lượt quét, không mở
  tính năng gói. 3 lượt dùng thử vẫn được dùng trước, sau đó mới trừ credit quà (FEFO).
- Lần đầu vào Trang chủ sau onboarding: pháo hoa + "Quà chào mừng: 10 lượt quét, dùng trong 5 ngày"
  (một lần mỗi user, nhớ bằng localStorage `catcheck.welcomeBonus.v1:<userId>`), rồi mới tới hướng
  dẫn tính năng. Kích hoạt mã thành công cũng bắn pháo hoa.
- Cấu hình: `catcheck.credit.welcome-bonus.enabled|amount|validity`
  (env `CATCHECK_WELCOME_BONUS_ENABLED`, `CATCHECK_WELCOME_BONUS_AMOUNT`, `CATCHECK_WELCOME_BONUS_VALIDITY`,
  vd. `5d`). Đổi cấu hình chỉ áp dụng cho tài khoản tặng sau đó.

## Mã credit (mã kích hoạt)

Seeder: `backend/src/main/java/com/catcheck/credit/application/DemoActivationCodeSeeder.java`.

- **Mỗi mã dùng đúng một lần.** Đã kích hoạt rồi thì nhập lại sẽ báo `ACTIVATION_CODE_ALREADY_USED`.
- Seeder idempotent: mã đã có trong DB thì bỏ qua, **không** reset mã đã dùng. Muốn có mã mới
  để demo tiếp, admin tự sinh lô mới (xem bên dưới).
- Hạn kích hoạt (`valid_until`) = thời điểm seed lần đầu + 365 ngày, lô `DEMO`.
- Nhập không phân biệt hoa/thường; gõ chữ `O` thay số `0` vẫn được chấp nhận.
- Gói nào không tồn tại hoặc đang tắt (`active = false`) thì mã của gói đó không được gieo.

Credit và thời hạn credit lấy từ `backend/src/main/resources/db/seed/R__seed_package_plan.sql`
(admin có thể đổi ở màn Gói; số liệu dưới đây là giá trị seed):

| Gói | Credit / mã | Credit hết hạn sau | Hồ sơ mèo tối đa |
|---|---|---|---|
| `MINI` | 3 | 7 ngày | 1 |
| `DAILY` | 8 | 7 ngày | 1 |
| `PLUS` | 10 | 7 ngày | 1 |
| `MULTI` | 16 | 7 ngày | không giới hạn |
| `CARE_BOX` | 16 | 7 ngày | không giới hạn |

| Gói | Mã |
|---|---|
| `MINI` | `CC-MINI-DEM000001C`, `CC-MINI-DEM000002B`, `CC-MINI-DEM000003A`, `CC-MINI-DEM0000049`, `CC-MINI-DEM0000058` |
| `DAILY` | `CC-DAILY-DEM000001C`, `CC-DAILY-DEM000002B`, `CC-DAILY-DEM000003A`, `CC-DAILY-DEM0000049`, `CC-DAILY-DEM0000058` |
| `PLUS` | `CC-PLUS-DEM000001C`, `CC-PLUS-DEM000002B`, `CC-PLUS-DEM000003A`, `CC-PLUS-DEM0000049`, `CC-PLUS-DEM0000058` |
| `MULTI` | `CC-MULTI-DEM000001C`, `CC-MULTI-DEM000002B`, `CC-MULTI-DEM000003A`, `CC-MULTI-DEM0000049`, `CC-MULTI-DEM0000058` |
| `CARE_BOX` | `CC-CARE_BOX-DEM000001C`, `CC-CARE_BOX-DEM000002B`, `CC-CARE_BOX-DEM000003A`, `CC-CARE_BOX-DEM0000049`, `CC-CARE_BOX-DEM0000058` |

### Mã mẫu đã hết hạn

| Mã | Gói | Lô | Kết quả khi kích hoạt |
|---|---|---|---|
| `CC-PLUS-EXP000001R` | `PLUS` | `DEMO-EXPIRED` | Bị từ chối `ACTIVATION_CODE_EXPIRED` (`valid_until` = lúc seed − 1 ngày) |

Lưu ý: `backend/tools/seed-demo.py` dùng riêng 4 mã `CC-MULTI-DEM0SEED…` (lô `DEMO-SEED-2026`)
cho tài khoản `demo@catcheck.vn`; không trùng với các mã ở trên.

## Admin quản lý mã

Đăng nhập `admin@catcheck.vn` → **Mã kích hoạt**:

- Danh sách hiển thị trạng thái **Chưa dùng** / **Đã dùng** / **Đã vô hiệu** / **Hết hạn**
  (Hết hạn = chưa dùng nhưng đã quá hạn kích hoạt, suy ra lúc truy vấn, không lưu DB), hạn kích
  hoạt và thời điểm đã dùng. Lọc được theo từng trạng thái, kể cả Hết hạn.
- **Sinh lô mã**: chọn gói, số lượng (≤ 50 000), tên lô (duy nhất), hạn kích hoạt (ngày). Mã sinh
  ngẫu nhiên, duy nhất theo `code_hash`; mã thô chỉ tải được **một lần** qua nút Tải CSV.
- Mã demo cố định ở trên không có CSV (đã có sẵn trong tài liệu này).
