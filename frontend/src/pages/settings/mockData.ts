/**
 * DỮ LIỆU THIẾT KẾ (DESIGN MOCK) cho các khối Cài đặt CHƯA có backend.
 *
 * Chép từ Figma `Web - 16. Cài đặt & Tùy chọn Ứng dụng` (1296×2141) và
 * `15. Hồ sơ & cài đặt` (438×2753). Những khối dưới đây KHÔNG có bảng/endpoint nào trong
 * `context/spec/parts/p4` và `p8`:
 *   - Khay cát & CleanBox IoT (thiết bị phần cứng)
 *   - Uỷ quyền hồ sơ y tế cho phòng khám
 *   - Đăng ký nhận cát định kỳ (subscription)
 *   - Hội viên ISFM / chứng chỉ ISO
 * Chúng được render có nhãn "Dữ liệu mẫu" để không ai nhầm là trạng thái thật của tài khoản.
 *
 * Phần CÓ backend thật (hồ sơ, bảo mật/MFA/phiên, tuỳ chọn thông báo, ngôn ngữ) KHÔNG nằm
 * ở đây — xem `features/auth` và `features/settings`.
 */

/** Khối hero đầu trang desktop (Web-16). */
export const SETTINGS_HERO = {
  systemBadge: "HỆ THỐNG TRỰC TUYẾN V2.8",
  syncLabel: "Đồng bộ Cloud AI",
};

/** Thẻ "Tiêu chuẩn Y tế" dưới cột điều hướng trái. */
export const SETTINGS_STANDARD_CARD = {
  body: "Mọi mẫu quang phổ và dữ liệu dải pH quang học đều được lưu trữ theo quy chuẩn mã hoá 256-bit y sinh.",
  certificateCode: "ISO/IEC-27001-VN",
};

/** Khối "Thiết bị & Tích hợp Khay Cát Thông Minh". */
export const SETTINGS_DEVICE = {
  statusLabel: "Đang hoạt động",
  name: "Khay Cát CleanBox Smart-01",
  location: "Phòng khách tầng 1",
  mac: "E4:5F:01:99:BC:4A",
  chips: ["Wifi 5GHz (Tín hiệu tốt)", "Pin 88%", "Sensor v3.4 (Chuẩn Calibrated)"],
  autoCaptureTitle: "Tự động chụp và phân tích màu cát",
  autoCaptureBody:
    "Kích hoạt camera quang phổ và LED 5000K chụp đáy khay sau khi cảm biến hồng ngoại xác nhận mèo đã rời khay 90 giây.",
};

/** Khối "Uỷ quyền Hồ sơ Y tế Thú cưng". */
export const SETTINGS_VET_SHARING = {
  clinicName: "Bệnh viện Thú y PetCare Thảo Điền",
  statusLabel: "Đã liên kết",
  scope: "Cấp quyền: Xem lịch sử quét màu cát 30 ngày gần nhất, đồ thị pH tự động & nhật ký sinh hoá tiểu tiện.",
  doctorName: "BS. Lê Hoài An",
  updatedAtLabel: "2 giờ trước",
  token: "PC-7782A",
};

/** Khối "Đăng ký nhận cát tự động (Subscription)". */
export const SETTINGS_SUBSCRIPTION = {
  savingBadge: "Gói Tiết Kiệm 15%",
  planName: "SmartSand Pro 3 Tháng",
  planNote: "Cát chỉ thị pH quang phổ kép (6 túi / 3 tháng)",
  nextDeliveryDate: "15 / 11 / 2025",
  nextDeliveryNote: "Giao hàng miễn phí hoả tốc 2H",
  address: "Số 42 Đường số 12, P. TP. Thủ Đức, TP. Hồ Chí Minh",
};

/** Hội viên thú y hiển thị ở thẻ thông tin tài khoản (không có cột nào trong `app_user`). */
export const SETTINGS_VET_MEMBERSHIP = {
  badge: "Đã xác thực ISFM Member",
  id: "ISFM-VN-2024-8902",
};

/** Hotline hỗ trợ ở nhóm "Hỗ trợ & Cộng đồng" (bản mobile). */
export const SETTINGS_SUPPORT_HOTLINE = {
  number: "1900-2828",
  hours: "8:00 - 21:00",
};

/**
 * Ngưỡng cảnh báo trong "Cấu hình Lịch nhắc & Cảnh báo Sức khoẻ" (Web-16).
 *
 * ⚠️ Mục `bloodTrace` nói về phát hiện vi máu — MÂU THUẪN quyết định #8
 * (`context/spec/00-decisions.md`: "Chỉ pH. Không phát hiện máu"). Giữ nguyên văn để khớp
 * thiết kế nhưng render ở trạng thái TẮT + không bật được, kèm ghi chú là tính năng không
 * có trong phạm vi sản phẩm — chờ owner quyết (xem M3 §14.a).
 */
export const SETTINGS_ALERT_THRESHOLDS = [
  {
    key: "phOutOfRange",
    title: "Gửi thông báo đẩy ngay khi pH ngoài dải tham chiếu",
    body: "Dải tham chiếu lấy từ `GET /reference/ph-bands`, không hard-code trong ứng dụng.",
    supported: true,
  },
  {
    key: "bloodTrace",
    title: "Cảnh báo nghi ngờ có vi máu hoặc vón cục bất thường",
    body: "Ngoài phạm vi sản phẩm — CatCheck chỉ ước lượng pH từ màu hạt chỉ thị, không phát hiện máu.",
    supported: false,
  },
  {
    key: "smsToVet",
    title: "Gửi tin nhắn SMS khẩn cấp tới Bác sĩ Thú y phụ trách khi có cảnh báo Đỏ",
    body: "Chưa có kênh SMS trong hệ thống thông báo hiện tại.",
    supported: false,
  },
] as const;

/** Phiên bản hiển thị ở chân trang Cài đặt (bản mobile). */
export const SETTINGS_APP_VERSION = "2.4.0";
