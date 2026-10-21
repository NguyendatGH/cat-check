import type { PolicyCode, PolicyView } from "./types";

export const DEFAULT_LEGAL_DOCUMENTS: Record<PolicyCode, PolicyView> = {
  TERMS: {
    policyType: "TERMS",
    version: "1.0",
    locale: "vi",
    title: "Điều khoản sử dụng dịch vụ CATCHECK",
    contentMd: `# Điều khoản sử dụng dịch vụ CATCHECK

Chào mừng bạn đến với CATCHECK. Bằng việc truy cập hoặc sử dụng ứng dụng và website CATCHECK, bạn đồng ý tuân thủ các điều khoản và điều kiện được nêu dưới đây.

## 1. Giới thiệu dịch vụ

CATCHECK là nền tảng giải pháp hỗ trợ chăm sóc sức khỏe mèo cưng thông qua công nghệ cát vệ sinh chỉ thị màu pH và ứng dụng di động phân tích hình ảnh. Dịch vụ bao gồm:
- Quét và nhận diện màu hạt cát chỉ thị pH từ ảnh chụp.
- Lưu trữ lịch sử và hiển thị biểu đồ xu hướng pH theo thời gian.
- Cung cấp danh bạ cơ sở thú y, đặt lịch hẹn và hướng dẫn chăm sóc mèo.
- Cung cấp sản phẩm cát thông minh và phụ kiện chăm sóc mèo qua Cửa hàng CATCHECK.

## 2. Tài khoản và trách nhiệm người dùng

- Bạn chịu trách nhiệm bảo mật thông tin tài khoản, mật khẩu và mã xác thực OTP.
- Bạn cam kết cung cấp thông tin chính xác khi tạo tài khoản và đặt hàng.
- Không sử dụng ứng dụng vào các mục đích gian lận, phá hoại hoặc can thiệp trái phép vào hệ thống.

## 3. Miễn trừ trách nhiệm y tế (Quan trọng)

- CATCHECK **hỗ trợ theo dõi sức khỏe tại nhà, không phải là thiết bị chẩn đoán y tế và không thay thế việc thăm khám của bác sĩ thú y**.
- Màu sắc hạt cát chỉ thị phản ánh xu hướng pH nước tiểu và có thể bị ảnh hưởng bởi ánh sáng, góc chụp hoặc tạp chất.
- Khi mèo có dấu hiệu bất thường (bí tiểu, tiểu ra máu, kêu la khi tiểu), bạn cần đưa bé đến cơ sở thú y gần nhất ngay lập tức.

## 4. Mua hàng, thanh toán và giao hàng

- CATCHECK hỗ trợ thanh toán khi nhận hàng (COD) và thanh toán trực tuyến qua cổng PayOS.
- Đơn hàng COD được xác nhận ngay sau khi đặt; quý khách thanh toán tiền mặt cho nhân viên giao hàng khi nhận kiện hàng.
- Mã kích hoạt lượt quét (credit) in trên bao bì có hiệu lực theo thời hạn của từng gói cát (Túi 1 kg: 10 credit/7 ngày; Túi 2,5 kg: 36 credit/30 ngày).

## 5. Quyền sở hữu trí tuệ

Toàn bộ thương hiệu CATCHECK, logo, giao diện, mã nguồn, thuật toán nhận diện màu và tài liệu hướng dẫn thuộc quyền sở hữu độc quyền của CATCHECK. Mọi hành vi sao chép trái phép đều bị nghiêm cấm.

## 6. Liên hệ hỗ trợ

Mọi thắc mắc về điều khoản, xin vui lòng gửi email về support@catcheck.vn hoặc hotline hỗ trợ khách hàng.`,
    contentUrl: null,
    effectiveFrom: "2026-01-01T00:00:00Z",
    requiresReconsent: false,
    summaryOfChanges: "Bản chính thức",
    affectedPurposes: [],
  },

  PRIVACY: {
    policyType: "PRIVACY",
    version: "1.0",
    locale: "vi",
    title: "Chính sách quyền riêng tư & Bảo vệ dữ liệu cá nhân",
    contentMd: `# Chính sách quyền riêng tư CATCHECK

CATCHECK cam kết bảo vệ dữ liệu cá nhân của người dùng tuân thủ theo Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15 và Nghị định số 356/2025/NĐ-CP.

## 1. Dữ liệu chúng tôi thu thập

- **Thông tin tài khoản:** Họ tên, địa chỉ email, số điện thoại, mật khẩu mã hóa BCrypt.
- **Hồ sơ thú cưng:** Tên bé mèo, giống, ngày sinh, cân nặng, tiền sử sức khỏe và lịch tiêm phòng.
- **Dữ liệu quét:** Ảnh chụp khay cát, giá trị dải màu pH nhận diện và thời gian ghi nhận.
- **Dữ liệu giao dịch:** Địa chỉ giao hàng, lịch sử đơn hàng và trạng thái thanh toán.
- **Dữ liệu vị trí:** Tọa độ GPS khi bạn cho phép (để tìm phòng khám thú y gần nhất quanh bạn).

## 2. Mục đích xử lý dữ liệu

- Cung cấp tính năng phân tích màu cát và theo dõi xu hướng pH.
- Xử lý đơn đặt hàng, giao hàng và đối soát thanh toán.
- Gửi thông báo nhắc nhở kiểm tra định kỳ cho bé mèo.
- Cải thiện độ chính xác của mô hình nhận diện màu (chỉ khi bạn đồng ý).

## 3. Quyền của chủ thể dữ liệu

Theo quy định pháp luật, bạn có đầy đủ các quyền sau trong Trung tâm quyền riêng tư (Privacy Center):
- **Quyền được biết & truy cập:** Xem toàn bộ dữ liệu CATCHECK lưu trữ về bạn.
- **Quyền rút lại sự đồng ý:** Bật/tắt các mục đích xử lý dữ liệu bất cứ lúc nào.
- **Quyền xuất dữ liệu (Data Export):** Tải về bản sao dữ liệu cá nhân định dạng nén chuẩn ZIP.
- **Quyền yêu cầu xóa tài khoản (Data Erasure):** Yêu cầu xóa vĩnh viễn tài khoản và toàn bộ hình ảnh, hồ sơ thú cưng sau 7 ngày ân hạn.

## 4. Bảo mật dữ liệu

Chúng tôi áp dụng các tiêu chuẩn an ninh nghiêm ngặt: mã hóa đầu cuối thông tin nhạy cảm, giao thức HTTPS/TLS, mã hóa mật khẩu một chiều BCrypt và xác thực hai lớp (2FA/TOTP) cho tài khoản quản trị.

## 5. Cán bộ bảo vệ dữ liệu (DPO)

Email liên hệ phụ trách bảo vệ dữ liệu: dpo@catcheck.vn. CATCHECK cam kết phản hồi các yêu cầu dữ liệu cá nhân trong vòng 02 ngày làm việc.`,
    contentUrl: null,
    effectiveFrom: "2026-01-01T00:00:00Z",
    requiresReconsent: false,
    summaryOfChanges: "Bản chính thức",
    affectedPurposes: [],
  },

  MEDICAL_DISCLAIMER: {
    policyType: "MEDICAL_DISCLAIMER",
    version: "1.0",
    locale: "vi",
    title: "Tuyên bố miễn trừ trách nhiệm y tế",
    contentMd: `# Tuyên bố miễn trừ trách nhiệm y tế

> **CATCHECK hỗ trợ theo dõi tại nhà — không thay thế chẩn đoán hoặc điều trị của bác sĩ thú y.**

## 1. Bản chất của thông tin theo dõi

CATCHECK ghi nhận màu sắc của các hạt chỉ thị trong cát vệ sinh và phân loại vào các dải tham chiếu pH. Kết quả hiển thị chỉ mang tính chất tham khảo, giúp chủ nuôi nhận biết sự thay đổi so với mức thường ngày của chính bé mèo.

## 2. Các yếu tố ảnh hưởng đến màu hạt cát

- Ánh sáng phòng và chất lượng camera điện thoại khi chụp.
- Thức ăn, nước uống, thuốc hoặc thực phẩm chức năng mèo đang sử dụng.
- Độ ẩm môi trường và thời gian tiếp xúc của nước tiểu với hạt cát.

## 3. Dấu hiệu khẩn cấp cần đến bác sĩ thú y NGAY LẬP TỨC

Nếu bạn nhận thấy các dấu hiệu sau ở mèo, **đừng chờ theo dõi thêm mà hãy đưa bé đến bệnh viện thú y ngay**:
- Bé rặn tiểu nhiều lần nhưng không ra nước tiểu hoặc chỉ ra vài giọt.
- Kêu gào, đau đớn khi bước vào khay vệ sinh.
- Có máu tươi hoặc vệt hồng sẫm trong nước tiểu.
- Bỏ ăn, nôn ói liên tục, lờ đờ hoặc bụng căng cứng.

Tắc nghẽn đường tiết niệu ở mèo là tình trạng cấp cứu nguy hiểm đến tính mạng trong vòng 24–48 giờ nếu không được can thiệp kịp thời.`,
    contentUrl: null,
    effectiveFrom: "2026-01-01T00:00:00Z",
    requiresReconsent: false,
    summaryOfChanges: "Bản chính thức",
    affectedPurposes: [],
  },

  COOKIE: {
    policyType: "COOKIE",
    version: "1.0",
    locale: "vi",
    title: "Chính sách cookie",
    contentMd: `# Chính sách cookie CATCHECK

CATCHECK chỉ sử dụng cookie cần thiết nhằm đảm bảo hoạt động an toàn của ứng dụng:

- **Cookie phiên đăng nhập (Session cookie):** Giúp duy trì trạng thái đăng nhập của bạn giữa các trang.
- **Cookie bảo mật XSRF-TOKEN:** Bảo vệ tài khoản chống lại các cuộc tấn công giả mạo yêu cầu (CSRF).

Hiện tại CATCHECK không sử dụng cookie theo dõi quảng cáo từ bên thứ ba.`,
    contentUrl: null,
    effectiveFrom: "2026-01-01T00:00:00Z",
    requiresReconsent: false,
    summaryOfChanges: "Bản chính thức",
    affectedPurposes: [],
  },
};
