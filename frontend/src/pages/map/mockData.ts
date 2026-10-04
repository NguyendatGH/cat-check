/**
 * DỮ LIỆU GIẢ (DESIGN MOCK) — KHÔNG CÓ BACKEND, VÀ SẼ KHÔNG CÓ Ở PHASE 1.
 *
 * `context/spec/parts/p4-domain-model-erd.md` xếp `place` và `place_review` vào **Phase 2**
 * với nguyên văn: "Không đặc tả chi tiết, không viết migration ở Phase 1." Vì vậy module
 * Bản đồ / Chi tiết phòng khám KHÔNG gọi API nào — toàn bộ nội dung dưới đây là dữ liệu
 * dựng tay cho phần giao diện, chép từ bản thiết kế Figma:
 *   - `1:2904`  12. Bản đồ Chăm sóc Mèo (mobile)
 *   - `1:2371`  13. Chi tiết Phòng khám Thú y (mobile)
 *   - `16:5393` Web - 12 & 13. Bản đồ Chăm sóc Mèo & Phòng khám Thú y
 *   - `16:2257` Web - 13. Chi tiết Phòng khám & Đặt lịch Khám Thú Y
 * đối chiếu thêm với `context/spec/reference/screens/M3-...md` §12/§13 và
 * `context/spec/reference/screens/W2-...md` §3/§4.
 *
 * Mọi export đều mang tiền tố `DESIGN_MOCK_` để khi Phase 2 nối API thật thì việc xoá file
 * này làm vỡ biên dịch ở đúng mọi chỗ còn dùng — đó là chủ đích.
 *
 * ⚠️ COPY ĐÃ LỆCH BẢN THIẾT KẾ MỘT CÁCH CÓ CHỦ ĐÍCH:
 *  (a) Quyết định #8 của owner (`context/spec/00-decisions.md`): "Chỉ số đo: **Chỉ pH**.
 *      Không phát hiện máu." Bản mockup màn 13 (mobile) hứa truyền "cảnh báo máu vi thể"
 *      sang phòng khám — đã bỏ hẳn. Theo thứ tự thẩm quyền ở `CLAUDE.md`, 00-decisions.md
 *      thắng mockup.
 *  (b) Test CI `REQ-COPY-01` (`p17` §17.10b) quét danh sách từ cấm y tế: `chẩn đoán`,
 *      `chữa`, `điều trị`, `khỏi bệnh`, `thuốc`, `kê đơn`, `an toàn 100%`,
 *      `chính xác tuyệt đối`. Các cụm trong mockup như "Chẩn đoán hình ảnh", "310 ca điều
 *      trị", "Minh bạch chi phí khám chữa bệnh", "100% Khám chữa thật", "Tỉ lệ dứt điểm
 *      94%", "Đã trị dứt điểm viêm bàng quang" đã được viết lại sang ngôn ngữ mô tả dịch
 *      vụ / quan sát, không hứa kết quả.
 *
 * ⚠️ RIÊNG TƯ: `p4` đánh dấu `LOCATION_MAP` là mục đích xử lý cần đồng ý riêng. Màn này
 * KHÔNG gọi geolocation của trình duyệt; "khu vực" dưới đây là hằng số do người dùng chọn
 * thủ công trong danh sách mẫu, khoảng cách cũng là số dựng sẵn — không suy ra từ vị trí
 * thật của ai.
 */

/*
 * ⛔ CHAN GO-LIVE — hai ảnh raster dưới đây là ẢNH CHỤP MÀN HÌNH GOOGLE MAPS lấy từ bản
 * export Figma: logo Google và dòng "Map data ©2026 Google" nằm NUNG trong pixel. Dùng ảnh
 * tĩnh kiểu này trong sản phẩm phát hành là vi phạm Google Maps Platform Terms of Service
 * (ảnh Maps chỉ được hiển thị qua API có khoá, kèm attribution động, không được chụp lại).
 *
 * Phải thay trước khi lên production. Quyết định #18 yêu cầu "giải pháp free ổn định, không
 * phải thay khi lên prod" — lựa chọn thuộc về owner, p4 đã đẩy sang Part 9 để đánh giá.
 * Đây CHƯA phải lựa chọn đó: nó chỉ là ảnh placeholder để dựng đúng bố cục Phase 2.
 */
import mapRasterWide from "@/shared/assets/images/web-map/map-hcmc-wide.png";
import mapRasterCity from "@/shared/assets/images/web-map/map-hcmc-city.png";
import clinicPetcareLobby from "@/shared/assets/images/web-map/clinic-petcare-lobby.jpg";
import clinicPetcareEndoscopy from "@/shared/assets/images/web-map/clinic-petcare-endoscopy.jpg";
import clinicPetcareQuietZone from "@/shared/assets/images/web-map/clinic-petcare-quiet-zone.jpg";
import clinicPetcarePod from "@/shared/assets/images/web-map/clinic-petcare-pod.jpg";
import clinicHappyPawsLobby from "@/shared/assets/images/web-map/clinic-happy-paws-lobby.jpg";
import doctorLanPhuong from "@/shared/assets/images/web-map/doctor-lan-phuong.jpg";
import doctorHoangNam from "@/shared/assets/images/web-map/doctor-hoang-nam.jpg";

/**
 * Ảnh nền bản đồ. Ứng dụng KHÔNG có thư viện bản đồ và KHÔNG gọi dịch vụ tile nào (quyết
 * định #18 mới dừng ở "chọn giải pháp map free", chưa chốt provider) — bản mockup vẽ bản đồ
 * bằng một ảnh raster, nên ở đây cũng vậy: ảnh tĩnh + marker định vị tuyệt đối theo % phía
 * trên. Ảnh được tách ra từ chính file SVG thiết kế.
 */
export const DESIGN_MOCK_MAP_RASTER_WIDE = mapRasterWide;
export const DESIGN_MOCK_MAP_RASTER_CITY = mapRasterCity;

/** Khu vực đang xem — hằng số, KHÔNG đọc từ geolocation. */
export const DESIGN_MOCK_AREA = "Thảo Điền, TP. Thủ Đức";
export const DESIGN_MOCK_PARTNER_COUNT = 42;
export const DESIGN_MOCK_CERTIFIED_COUNT = 18;
export const DESIGN_MOCK_NEARBY_COUNT = 14;

export type MockPlaceKind = "clinic" | "emergency" | "lab";

/** Màu ghim trên bản đồ — tên vai trò, ánh xạ sang token Tailwind ở phía component. */
export type MockPinTone = "featured" | "emergency" | "clinic" | "muted";

export interface MockMapPin {
  /** Toạ độ theo % khung ảnh bản đồ (suy từ hình học marker trong SVG thiết kế). */
  leftPct: number;
  topPct: number;
  tone: MockPinTone;
  /** Nhãn nổi cạnh ghim (một số ghim trong thiết kế không có nhãn). */
  label?: string;
}

export interface MockPlace {
  id: string;
  name: string;
  /** Tên rút gọn cho danh sách hẹp của bản mobile. */
  shortName: string;
  kind: MockPlaceKind;
  address: string;
  area: string;
  /** Khoảng cách chỉ có với fixture cũ; API không cung cấp vị trí của người dùng để tính. */
  distanceKm?: number;
  rating: number;
  reviewCount: number;
  /** Số lượt khám đã ghi nhận (mockup gốc ghi "310 ca điều trị" — xem ghi chú copy ở đầu file). */
  visitCount: number;
  openLabel: string;
  /** `danger` dành cho cơ sở trực đêm — thiết kế tô đỏ dòng này. */
  openTone: "success" | "danger";
  hotlineLabel?: string;
  badges: string[];
  leadDoctor: string;
  amenity: string;
  summary: string;
  specialties: string[];
  certification?: string;
  phone: string;
  pin: MockMapPin;
  featured: boolean;
  latitude?: number;
  longitude?: number;
}

export const DESIGN_MOCK_PLACES: MockPlace[] = [
  {
    id: "petcare-thao-dien",
    name: "Bệnh viện Thú y PetCare Center Thảo Điền",
    shortName: "Bệnh viện Thú y PetCare Center",
    kind: "clinic",
    address: "124A Xuân Thủy, Phường Thảo Điền, TP. Thủ Đức, TP. Hồ Chí Minh",
    area: "Thảo Điền, TP. Thủ Đức",
    distanceKm: 1.2,
    rating: 4.9,
    reviewCount: 310,
    visitCount: 310,
    openLabel: "Đang mở cửa • Đóng lúc 20:30",
    openTone: "success",
    hotlineLabel: "Hotline cấp cứu mèo 24/7 sẵn sàng",
    badges: ["ISFM Cat-Friendly Gold", "Trạm mẫu CATCHECK"],
    leadDoctor: "ThS. BS. Lan Phương",
    amenity: "Phòng chờ riêng cho Mèo (Quiet Zone)",
    summary:
      "Feline Renal Center đạt chuẩn quốc tế ISFM Gold, khu cách âm hoàn toàn, không nhận chó nên bé mèo bớt căng thẳng khi chờ.",
    specialties: ["Chuyên khoa Tiết niệu", "Siêu âm thận - bàng quang", "Xét nghiệm nước tiểu tại chỗ 15 phút"],
    certification: "Chứng nhận Vàng Thân thiện với Mèo (ISFM Gold)",
    phone: "1900 8899",
    pin: { leftPct: 50, topPct: 52, tone: "featured", label: "PetCare Thảo Điền" },
    featured: true,
  },
  {
    id: "samyang-anipol",
    name: "Phòng Khám Thú Y Samyang Anipol",
    shortName: "Phòng khám Thú y Samyang Anipol",
    kind: "clinic",
    address: "Số 35 Song Hành, Phường An Phú, TP. Thủ Đức, TP. Hồ Chí Minh",
    area: "An Phú, TP. Thủ Đức",
    distanceKm: 2.8,
    rating: 4.8,
    reviewCount: 184,
    visitCount: 184,
    openLabel: "Mở đến 21:00",
    openTone: "success",
    badges: ["Tiêu chuẩn Hàn Quốc", "Khám Tiết Niệu & Siêu Âm 4D"],
    leadDoctor: "BS. Kim Seo Yeon",
    amenity: "Khu lưu bệnh tách riêng theo cá thể",
    summary:
      "Chuyên sâu về sỏi tiết niệu canxi oxalate và viêm bàng quang vô căn FIC ở mèo đực, dùng thiết bị nội soi niệu đạo cỡ nhỏ.",
    specialties: ["Nội soi niệu đạo", "Siêu âm 4D bàng quang", "Theo dõi FIC dài ngày"],
    phone: "1900 6886",
    pin: { leftPct: 70.7, topPct: 75, tone: "clinic", label: "Samyang An Phú" },
    featured: false,
  },
  {
    id: "petpro-24-7",
    name: "Trung tâm Hồi sức Cấp cứu Mèo PetPro 24/7",
    shortName: "Trung tâm Hồi sức Cấp cứu Mèo",
    kind: "emergency",
    address: "68 Nguyễn Văn Hưởng, Phường Thảo Điền, TP. Thủ Đức, TP. Hồ Chí Minh",
    area: "Thảo Điền, TP. Thủ Đức",
    distanceKm: 4.1,
    rating: 4.7,
    reviewCount: 96,
    visitCount: 96,
    openLabel: "Mở cả ngày đêm",
    openTone: "danger",
    hotlineLabel: "Cấp cứu xuyên đêm 24/7",
    badges: ["Cấp Cứu Xuyên Đêm 24/7"],
    leadDoctor: "BS. Trần Quốc Huy",
    amenity: "Băng ca và lồng oxy trực sẵn",
    summary: "Nhận ca bí tiểu, tắc niệu đạo và sốc nhiệt ngoài giờ hành chính; có bác sĩ nội trú túc trực suốt đêm.",
    specialties: ["Hồi sức bí tiểu", "Lồng oxy", "Truyền dịch cấp"],
    phone: "1900 8899",
    pin: { leftPct: 29.8, topPct: 32, tone: "emergency", label: "PetPro 24/7" },
    featured: false,
  },
  {
    id: "happy-paws-feline",
    name: "Bệnh viện & Phòng khám Thú y Happy Paws",
    shortName: "Bệnh viện Thú y Happy Paws",
    kind: "lab",
    address: "142 Nguyễn Văn Hưởng, Phường Thảo Điền, TP. Thủ Đức, TP. Hồ Chí Minh",
    area: "Thảo Điền, TP. Thủ Đức",
    distanceKm: 2.1,
    rating: 4.8,
    reviewCount: 184,
    visitCount: 184,
    openLabel: "Mở đến 22:00",
    openTone: "success",
    hotlineLabel: "Hotline cấp cứu mèo 24/7 sẵn sàng",
    badges: ["ISFM Cat-Friendly Gold", "Trạm mẫu CATCHECK"],
    leadDoctor: "BS. Lan Anh",
    amenity: "Lab xét nghiệm nước tiểu tại chỗ",
    summary: "Trạm nhận mẫu hạt chỉ thị CATCHECK, trả kết quả soi cặn nước tiểu trong 15 phút ngay tại quầy.",
    specialties: ["Chuyên khoa Tiết niệu", "Siêu âm thận bàng quang", "Xét nghiệm nước tiểu tại chỗ 15 phút"],
    certification: "Chứng nhận Vàng Thân thiện với Mèo (ISFM Gold)",
    phone: "1900 6886",
    pin: { leftPct: 24.3, topPct: 70, tone: "muted", label: "Happy Paws" },
    featured: false,
  },
];

export interface MockFilterChip {
  id: "all" | "clinic" | "emergency" | "isfm" | "lab";
  count: number;
}

export const DESIGN_MOCK_FILTERS: MockFilterChip[] = [
  { id: "all", count: 28 },
  { id: "clinic", count: 14 },
  { id: "emergency", count: 6 },
  { id: "isfm", count: 9 },
  { id: "lab", count: 8 },
];

/** Hàng 4 thẻ hỗ trợ nhanh ở chân bản web. `icon` là tên vai trò, component tự ánh xạ. */
export interface MockQuickService {
  id: string;
  icon: "decode" | "hotline" | "courier" | "alert";
  title: string;
  body: string;
  tone: "secondary" | "primary" | "success" | "danger";
}

export const DESIGN_MOCK_QUICK_SERVICES: MockQuickService[] = [
  {
    id: "decode",
    icon: "decode",
    title: "Giải mã màu cát tức thì",
    body: "Phân loại tự động 5 nhóm chỉ báo sinh hoá bàng quang theo bảng màu đang hiệu lực.",
    tone: "secondary",
  },
  {
    id: "hotline",
    icon: "hotline",
    title: "Đường dây nóng Feline",
    body: "1900 6886 — bác sĩ thường trực giải đáp biến chuyển màu cát.",
    tone: "primary",
  },
  {
    id: "courier",
    icon: "courier",
    title: "Giao mẫu cấp tốc",
    body: "Nhận mẫu hạt chỉ thị tại nhà, gửi tới phòng xét nghiệm trong 2 giờ.",
    tone: "success",
  },
  {
    id: "alert",
    icon: "alert",
    title: "Báo động đỏ (Bí tiểu)",
    body: "Mèo rặn tiểu trên 20 phút là tình huống cấp cứu. Xem cơ sở thú y gần nhất.",
    tone: "danger",
  },
];

/* ------------------------------------------------------------------------- *
 * Chi tiết phòng khám (`/map/clinics/:clinicId`)
 * ------------------------------------------------------------------------- */

export interface MockClinicPhoto {
  src: string;
  caption?: string;
}

export interface MockClinicService {
  id: string;
  icon: "flask" | "scan" | "paw" | "droplet";
  title: string;
  body: string;
}

export interface MockClinicDoctor {
  id: string;
  photo: string;
  name: string;
  role: string;
  bio: string;
  tags: string[];
  /** Cố vấn chuyên môn CATCHECK — chỉ bác sĩ đầu tiên có. */
  isAdvisor: boolean;
}

export interface MockClinicPrice {
  id: string;
  title: string;
  body: string;
  badge: string;
  badgeTone: "success" | "info" | "secondary";
  price: number;
  compareAtPrice: number;
}

export interface MockClinicReview {
  id: string;
  initials: string;
  author: string;
  petLabel: string;
  rating: number;
  tag: string;
  timeAgo: string;
  body: string;
}

export interface MockClinicDetail {
  placeId: string;
  breadcrumbArea: string;
  goldStandardBadge: string;
  /** Chip nhỏ trên ảnh ở popup bản đồ. */
  popupBadge: string;
  statusBadges: string[];
  photos: MockClinicPhoto[];
  photoTotal: number;
  moreAngles: number;
  highlights: { id: string; icon: "kidney" | "mute"; title: string; body: string }[];
  hours: string;
  /** Bản rút gọn cho hàng "Giờ khám thường" của bản mobile (một dòng, như Figma `1:2371`). */
  hoursShort: string;
  emergencyHours: string;
  mapAreaLabel: string;
  services: MockClinicService[];
  doctors: MockClinicDoctor[];
  prices: MockClinicPrice[];
  priceSavingPercent: number;
  reviews: MockClinicReview[];
}

/**
 * Bản thiết kế chỉ vẽ chi tiết cho MỘT cơ sở; các cơ sở còn lại dùng chung khối chi tiết
 * này (chỉ đổi phần thông tin lấy từ `MockPlace`). Phase 2 sẽ thay bằng `GET /places/{id}`.
 */
export const DESIGN_MOCK_CLINIC_DETAIL: MockClinicDetail = {
  placeId: "petcare-thao-dien",
  breadcrumbArea: "TP. Thủ Đức",
  goldStandardBadge: "ISFM Cat-Friendly Clinic Gold Standard",
  popupBadge: "Cat-Friendly",
  statusBadges: ["Đang mở cửa • Đón bệnh", "Trạm xét nghiệm CATCHECK"],
  photos: [
    { src: clinicPetcareLobby, caption: "Sảnh tiếp nhận Cat-only" },
    { src: clinicPetcareEndoscopy, caption: "Phòng mổ nội soi 4D" },
    { src: clinicPetcareQuietZone, caption: "Khu chờ yên tĩnh dành cho mèo" },
    { src: clinicPetcarePod, caption: "Khoang lưu trú cách âm" },
    { src: clinicHappyPawsLobby, caption: "Quầy nhận mẫu CATCHECK" },
  ],
  photoTotal: 14,
  moreAngles: 8,
  highlights: [
    {
      id: "renal",
      icon: "kidney",
      title: "Chuyên khoa Tiết niệu & Thận Mèo",
      body: "Feline Renal Center đạt chuẩn quốc tế ISFM Gold.",
    },
    {
      id: "quiet",
      icon: "mute",
      title: "Quiet Cat-Only Waiting Lounge",
      body: "Khu cách âm hoàn toàn, không có chó, giảm stress cho bé mèo.",
    },
  ],
  hours: "08:00 - 20:30 (Thứ 2 - Chủ Nhật) • Trực cấp cứu 24/7 qua đường dây nóng",
  hoursShort: "Thứ 2 - CN: 08:00 - 20:30",
  emergencyHours: "24 Giờ / 7 Ngày",
  mapAreaLabel: "Khu Y tế Thảo Điền",
  services: [
    {
      id: "urine-test",
      icon: "flask",
      title: "Xét nghiệm nước tiểu tại chỗ 15 phút",
      body: "Định lượng tinh thể, pH và đạm niệu ngay trong lượt khám.",
    },
    {
      id: "ultrasound",
      icon: "scan",
      // Mockup gốc: "Chẩn đoán hình ảnh thành bàng quang và bể thận" — bỏ "Chẩn đoán" (REQ-COPY-01).
      title: "Siêu âm Thận - Bàng quang",
      body: "Ghi hình thành bàng quang và bể thận bằng đầu dò tần số cao.",
    },
    {
      id: "low-stress",
      icon: "paw",
      title: "Chăm sóc Giảm căng thẳng (Không an thần)",
      body: "Quy trình thao tác nhẹ nhàng kết hợp khuếch tán pheromone.",
    },
    {
      id: "nutrition",
      icon: "droplet",
      title: "Tiết niệu & Chế độ dinh dưỡng chuyên biệt",
      body: "Tư vấn khẩu phần theo nhóm tinh thể Struvite/Oxalate chuẩn y khoa.",
    },
  ],
  doctors: [
    {
      id: "lan-phuong",
      photo: doctorLanPhuong,
      name: "ThS. BS. CK1 Lan Phương",
      role: "Cố vấn chuyên môn CATCHECK",
      bio: "12 năm kinh nghiệm lâm sàng Feline Medicine. Chứng chỉ chuyên gia Tiết niệu & Bệnh học Mèo quốc tế từ Hiệp hội Thú y Mèo ISFM.",
      // Mockup gốc: "Tỉ lệ dứt điểm 94%" — là lời hứa kết quả, đổi sang chỉ số theo dõi.
      tags: ["FLUTD Specialist", "94% ca có tái khám đúng hẹn"],
      isAdvisor: true,
    },
    {
      id: "hoang-nam",
      photo: doctorHoangNam,
      name: "BS. Hoàng Nam",
      // Mockup gốc: "Chẩn đoán hình ảnh & Can thiệp" — bỏ "Chẩn đoán" (REQ-COPY-01).
      role: "Hình ảnh học & Can thiệp",
      bio: "Chuyên gia siêu âm vi mạch bàng quang, thao tác thông niệu đạo khẩn cấp và tán sỏi Struvite/Oxalate không xâm lấn.",
      tags: ["Siêu âm 4D niệu", "9 năm lâm sàng"],
      isAdvisor: false,
    },
  ],
  priceSavingPercent: 40,
  prices: [
    {
      id: "general-exam",
      title: "Khám lâm sàng tổng quát mèo",
      body: "Đo nhịp tim, nhiệt độ, kiểm tra bóng đái, khoang miệng và chỉ số cân nặng.",
      badge: "Thành viên CATCHECK",
      badgeTone: "success",
      price: 180000,
      compareAtPrice: 250000,
    },
    {
      id: "ultrasound-4d",
      title: "Siêu âm hệ tiết niệu & bàng quang 4D",
      body: "Kiểm tra cặn vách bàng quang, đo bề dày thành niệu và tầm soát sớm sỏi thận.",
      badge: "Độ nét cao",
      badgeTone: "info",
      price: 250000,
      compareAtPrice: 350000,
    },
    {
      id: "microscopy",
      title: "Soi kính hiển vi cặn nước tiểu & tinh thể Struvite",
      body: "Áp dụng khi xuất trình mã kết quả đổi màu cát từ ứng dụng CATCHECK.",
      badge: "MIỄN PHÍ 100%",
      badgeTone: "secondary",
      price: 0,
      compareAtPrice: 120000,
    },
  ],
  reviews: [
    {
      id: "review-thanh-hang",
      initials: "TH",
      author: "Thanh Hằng & Bé Bơ",
      petLabel: "Mèo Anh tai cụp",
      rating: 5,
      // Mockup gốc: "Đã trị dứt điểm viêm bàng quang" — là lời hứa kết quả, đổi sang mô tả theo dõi.
      tag: "Đã theo dõi viêm bàng quang",
      timeAgo: "3 ngày trước",
      body: "Lúc quét cát thấy màu chuyển tím đậm báo pH 7.8, mình đặt lịch ngay với BS Lan Phương ở PetCare. Nhờ dữ liệu CATCHECK gửi sẵn, bác sĩ nắm ngay lịch sử chỉ số, siêu âm phát hiện cặn bùn kịp thời nên không phải đặt sonde đau đớn. Phòng chờ riêng cho mèo cực kỳ yên tĩnh!",
    },
    {
      id: "review-quang-dung",
      initials: "QD",
      author: "Quang Dũng & Bé Simba",
      petLabel: "Mèo ta lông ngắn",
      rating: 5,
      tag: "Soi cặn Struvite miễn phí",
      timeAgo: "1 tuần trước",
      body: "Bác sĩ Nam siêu âm rất nhẹ nhàng. Simba nhà mình nhát lắm mà vô phòng chờ Cat-only nằm ngoan re re. Ưu đãi quét cát CATCHECK được soi kính hiển vi miễn phí 100%, nhân viên giải thích rất rõ ràng.",
    },
    {
      id: "review-minh-hoang",
      initials: "MH",
      author: "Minh Hoàng",
      petLabel: "Nuôi mèo Mochi (British Shorthair)",
      rating: 5,
      tag: "Xét nghiệm cặn nước tiểu",
      timeAgo: "2 ngày trước",
      // Mockup gốc kết bằng "Sỏi struvite đã tan hoàn toàn sau 3 tuần" — lời hứa kết quả, đổi sang mô tả theo dõi.
      body: "Nhờ quét cát phát hiện pH 7.8, BS Lan Anh làm xét nghiệm cặn nước tiểu ngay lập tức mà không cần tiêm an thần. Tái khám sau 3 tuần, chỉ số pH của Mochi đã về lại vùng ổn định.",
    },
    {
      id: "review-thao-le",
      initials: "TL",
      author: "Thảo Lê",
      petLabel: "Nuôi 2 bé mèo (Bánh Mì & Nem)",
      rating: 5,
      tag: "Gửi báo cáo PDF trước khi tới",
      timeAgo: "1 tuần trước",
      body: "Phòng khám sạch nhất Thảo Điền. Bác sĩ đã xem trước bản xuất PDF mình gửi từ app trước khi mình bước xuống taxi. Quy trình nhanh, tận tình và minh bạch.",
    },
  ],
};

/* ------------------------------------------------------------------------- *
 * Panel đặt lịch (bản web). Hoàn toàn cục bộ, KHÔNG gửi đi đâu.
 * ------------------------------------------------------------------------- */

export interface MockBookingService {
  id: string;
  label: string;
  icon: "droplet" | "scan" | "stethoscope" | "siren";
}

export interface MockBookingDay {
  id: string;
  weekdayLabel: string;
  dayNumber: string;
  slotsLeft: number;
}

export interface MockBookingPanel {
  catLabel: string;
  syncBadge: string;
  services: MockBookingService[];
  defaultServiceId: string;
  doctorId: string;
  doctorBadge: string;
  days: MockBookingDay[];
  defaultDayId: string;
  slotDateLabel: string;
  timeSlots: string[];
  defaultTimeSlot: string;
  symptomNote: string;
  serviceCostLabel: string;
  serviceCost: number;
  voucherAmount: number;
  totalCost: number;
  emergencyPhone: string;
}

export const DESIGN_MOCK_BOOKING: MockBookingPanel = {
  catLabel: "Luna (Mèo Anh lông ngắn - 2.5 tuổi) • pH 7.4 Kiềm",
  syncBadge: "Đã đồng bộ Bio-Kit C10",
  services: [
    { id: "urinary", label: "Tiết niệu & Nước tiểu", icon: "droplet" },
    { id: "ultrasound", label: "Siêu âm bàng quang", icon: "scan" },
    { id: "general", label: "Khám tổng quát định kỳ", icon: "stethoscope" },
    { id: "emergency", label: "Cấp cứu bí tiểu", icon: "siren" },
  ],
  defaultServiceId: "urinary",
  doctorId: "lan-phuong",
  doctorBadge: "Chuyên gia FLUTD",
  days: [
    { id: "d28", weekdayLabel: "", dayNumber: "28", slotsLeft: 0 },
    { id: "d29", weekdayLabel: "T4", dayNumber: "29", slotsLeft: 4 },
    { id: "d30", weekdayLabel: "T5", dayNumber: "30", slotsLeft: 8 },
    { id: "d31", weekdayLabel: "T6", dayNumber: "31", slotsLeft: 12 },
  ],
  defaultDayId: "d29",
  slotDateLabel: "29/10",
  timeSlots: ["09:00", "10:30", "14:00", "16:30", "18:00"],
  defaultTimeSlot: "10:30",
  symptomNote:
    "Bé Luna có dấu hiệu ngồi khay lâu, hạt cát bio chuyển lam kiềm pH 7.4 trong 2 ngày qua. Nhờ bác sĩ xem giúp xu hướng 30 ngày đính kèm.",
  serviceCostLabel: "Khám chuyên khoa & Siêu âm 4D",
  serviceCost: 430000,
  voucherAmount: 150000,
  totalCost: 280000,
  emergencyPhone: "1900 8899",
};

/** Định dạng tiền VND — bản map tự giữ một bản, không dùng chung với module khác. */
export function formatVnd(value: number): string {
  return `${value.toLocaleString("vi-VN")} đ`;
}

/** Tra cứu cơ sở theo id, rơi về cơ sở nổi bật nếu id không khớp (dữ liệu mẫu). */
export function findMockPlace(placeId: string | undefined): MockPlace {
  return DESIGN_MOCK_PLACES.find((p) => p.id === placeId) ?? DESIGN_MOCK_PLACES[0];
}
