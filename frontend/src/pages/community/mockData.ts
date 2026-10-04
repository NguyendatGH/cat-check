/**
 * DỮ LIỆU GIẢ (DESIGN MOCK) — KHÔNG CÓ BACKEND, KHÔNG GỌI API.
 *
 * Module Cộng đồng là **Phase 2**. `context/spec/parts/p4-domain-model-erd.md` ghi hai bảng
 * `post` và `comment` ở mục Phase 2 kèm nguyên văn: "Không đặc tả chi tiết, không viết
 * migration ở Phase 1." Vì vậy KHÔNG có endpoint, KHÔNG có schema, KHÔNG có hook query nào
 * cho màn này — toàn bộ nội dung dưới đây là dữ liệu dựng tay để UI khớp thiết kế Figma:
 *   - `11. Bảng tin Cộng đồng (Community Feed)` (mobile 390)
 *   - `Chi tiết Thảo luận Cộng đồng` (mobile 390)
 *   - `Web - 15. Cộng đồng & Thảo luận Y khoa (Community Feed & Discussions)` (web 1280)
 *   - `Web - Chi tiết Thảo luận Ca Bệnh & Ý kiến Bác sĩ Thú y` (web 1280)
 *
 * Mọi export đều mang tiền tố `DESIGN_MOCK_` để khi Phase 2 nối API thật thì xoá nguyên file
 * này, mọi nơi import sẽ báo lỗi biên dịch — chủ đích để không sót chỗ nào.
 *
 * ⚠️ NỘI DUNG ĐÃ LỆCH BẢN THIẾT KẾ MỘT CÁCH CÓ CHỦ ĐÍCH.
 * Bản Figma viết nhiều câu hứa phát hiện máu / hồng cầu vi thể ("kèm vệt hồng vi thể",
 * "chấm đỏ/nâu đậm — dấu hiệu vi thể xuất huyết bàng quang") và ngôn ngữ kê toa ("phác đồ
 * xử trí", "uống thuốc tan sỏi", "đã điều trị ổn định", "ca bệnh đã khỏi", "đã từng trị sỏi").
 * Hai nhóm này mâu thuẫn:
 *   1. quyết định #8 của owner ở `context/spec/00-decisions.md` — "Chỉ số đo: Chỉ pH. Không
 *      phát hiện máu";
 *   2. danh sách từ cấm y tế mà test CI REQ-COPY-01 quét (`p17` §17.10b).
 * Theo thứ tự thẩm quyền ở CLAUDE.md, `00-decisions.md` thắng và bản mockup không nằm trong
 * chuỗi thẩm quyền. Toàn bộ các câu đó đã được viết lại sang ngôn ngữ quan sát pH / theo dõi.
 *
 * ⚠️ MỌI CON SỐ pH chỉ tồn tại ở file này (ESLint cấm hard-code ngưỡng pH trong JSX).
 */

import postCatFountain from "@/shared/assets/images/web-community/post-cat-fountain.jpg";
import postLitterScanMacro from "@/shared/assets/images/web-community/post-litter-scan-macro.jpg";
import postLitterPurple from "@/shared/assets/images/web-community/post-litter-purple.jpg";
import postUltrasound from "@/shared/assets/images/web-community/post-ultrasound.jpg";
import postLitterBluePatch from "@/shared/assets/images/web-community/post-litter-blue-patch.jpg";
import avatarMinhAnh from "@/shared/assets/images/web-community/avatar-minh-anh.jpg";
import avatarHoangNam from "@/shared/assets/images/web-community/avatar-hoang-nam.jpg";
import avatarThaoLe from "@/shared/assets/images/web-community/avatar-thao-le.jpg";
import avatarMeMiuMiu from "@/shared/assets/images/web-community/avatar-me-miu-miu.jpg";
import avatarVetClinic from "@/shared/assets/images/web-community/avatar-vet-clinic.jpg";
import avatarVetLanPhuong from "@/shared/assets/images/web-community/avatar-vet-lan-phuong.jpg";
import avatarHoangNamBo from "@/shared/assets/images/web-community/avatar-hoang-nam-bo.jpg";
import avatarThuTrang from "@/shared/assets/images/web-community/avatar-thu-trang.jpg";
import catMiuMiu from "@/shared/assets/images/web-community/cat-miu-miu.jpg";
import railMark from "@/shared/assets/images/web-community/rail-mark.jpg";

export const DESIGN_MOCK_RAIL_MARK = railMark;

/** Tông màu nhãn — ánh xạ sang token `@theme`, tránh hex trong `.tsx` (ESLint cấm). */
export type MockTone = "primary" | "secondary" | "success" | "danger" | "neutral";

export interface MockAuthor {
  name: string;
  /** Phần trong ngoặc ngay sau tên, ví dụ "(Mẹ của Luna)". */
  nameSuffix?: string;
  avatarUrl: string;
  /** Nhãn vai trò bên phải tên (ví dụ "Sen xác thực", "Bác sĩ thú y tham vấn"). */
  roleBadge?: string;
  roleBadgeTone?: MockTone;
  /** Dòng phụ dưới tên. */
  meta: string;
}

/**
 * Đoạn văn trộn chữ và chip pH. Thiết kế chèn chip "pH 7.4" NGAY GIỮA câu, nên nội dung
 * phải tách thành mảnh thay vì một chuỗi — đồng thời giữ mọi con số pH ở file dữ liệu này
 * (ESLint cấm literal ngưỡng pH trong JSX).
 */
export type MockBodySegment = { kind: "text"; value: string } | { kind: "ph"; value: string; tone: MockTone };

/* ============================ 1. Bảng tin — bản MOBILE (390) ============================ */

/** Khối "LƯU Ý AN TOÀN CỘNG ĐỒNG" trên đầu bảng tin mobile. */
export const DESIGN_MOCK_SAFETY_NOTICE = {
  /**
   * Bản Figma: "…không thay thế cho chẩn đoán thú y". Từ "chẩn đoán" nằm trong danh sách
   * cấm của REQ-COPY-01 nên viết lại thành "việc thăm khám tại cơ sở thú y".
   */
  body: "Thảo luận trong cộng đồng mang tính chất chia sẻ kinh nghiệm cá nhân và không thay thế việc thăm khám tại cơ sở thú y. Vui lòng đưa bé đến cơ sở thú y khi có triệu chứng khẩn cấp.",
};

export interface MockMobilePost {
  id: string;
  author: MockAuthor;
  /** Nội dung bài, trộn chữ với chip pH đúng vị trí thiết kế. */
  body: MockBodySegment[];
  photoUrl?: string;
  photoCaption?: string;
  /** Dải "Ghi nhận chỉ thị CATCHECK: pH 7.4 → 6.8". */
  indicatorStrip?: { label: string; value: string };
  /** Hộp ghi chú chuyên môn gắn dưới bài. */
  expertNote?: { title: string; subtitle: string; body: string };
  /** Hộp gợi ý trung tính (bài 2 không có bác sĩ). */
  tipNote?: { title: string; body: string };
  likeCount: number;
  commentCount: number;
  saveCount: number;
}

export const DESIGN_MOCK_MOBILE_POSTS: MockMobilePost[] = [
  {
    id: "m-post-luna-ph",
    author: {
      name: "Minh Anh",
      nameSuffix: "(Mẹ của Luna)",
      avatarUrl: avatarMinhAnh,
      roleBadge: "Sen xác thực",
      roleBadgeTone: "primary",
      meta: "2 giờ trước",
    },
    body: [
      { kind: "text", value: "Luna vừa có chỉ số hơi kiềm (" },
      { kind: "ph", value: "pH 7.4", tone: "secondary" },
      { kind: "text", value: ") sau khi mình đổi sang pate cá hồi hôm thứ Ba. Đã trở về " },
      { kind: "ph", value: "pH 6.8", tone: "primary" },
      {
        kind: "text",
        value:
          " lý tưởng sau khi thêm vòi nước chảy tuần hoàn! Có ba mẹ nào thấy thức ăn làm đổi màu cát giống vậy không?",
      },
    ],
    photoUrl: postCatFountain,
    photoCaption: "Vòi nước tuần hoàn",
    indicatorStrip: { label: "Ghi nhận chỉ thị CATCHECK:", value: "pH 7.4 → 6.8" },
    expertNote: {
      title: "Ghi chú chuyên môn từ BS. Nguyễn Lan Anh",
      subtitle: "Chuyên gia Dinh dưỡng Thú y",
      body: 'Thay đổi khẩu phần ăn thường gây ra "thủy triều kiềm" sinh lý sau ăn trong 4–6 giờ khi cơ thể tiêu hóa protein. Nước uống sạch chảy liên tục giúp cân bằng lại pH nước tiểu tối ưu.',
    },
    likeCount: 24,
    commentCount: 8,
    saveCount: 14,
  },
  {
    id: "m-post-scan-light",
    author: {
      name: "Hoàng Nam",
      avatarUrl: avatarHoangNam,
      roleBadge: "Nhà nuôi nhiều bé mèo",
      roleBadgeTone: "secondary",
      meta: "5 giờ trước",
    },
    body: [
      {
        kind: "text",
        value:
          "Góc chia sẻ: Đặt ánh sáng thế nào để camera quét hạt cát chuẩn nhất? Mình thấy ánh sáng tự nhiên buổi sáng giúp khung quét nhận diện màu hạt chính xác nhất mà không bị chói đèn flash.",
      },
    ],
    tipNote: {
      title: "Khuyến nghị góc quét:",
      body: "Ánh sáng gián tiếp phân tán đều giúp tránh sai lệch màu hạt.",
    },
    likeCount: 15,
    commentCount: 12,
    saveCount: 0,
  },
];

/* ============================= 2. Bảng tin — bản WEB (1280) ============================= */

export const DESIGN_MOCK_WEB_HEADER = {
  memberBadge: "12.4k Thành viên tích cực",
  sponsorBadge: "Bảo trợ chuyên môn ISFM",
  title: "Cộng Đồng Y Khoa Feline CATCHECK",
  subtitle:
    "Không gian trao đổi lâm sàng, giải mã màu sắc hạt cát chỉ thị pH và kinh nghiệm chăm sóc sức khỏe tiết niệu mèo cưng cùng chuyên gia.",
  /** Chữ cái trong ô avatar của khung soạn bài nhanh (thiết kế web ghi "M"). */
  composerAvatarInitial: "M",
};

/** Nhãn bệnh lý gợi ý trong ô soạn bài nhanh ở bảng tin web. */
export const DESIGN_MOCK_COMPOSER_TAGS = ["#FLUTD", "#ĐổiMàuHạtCát", "#NướcTiểuVàngĐậm", "#ĐộKiềmpH"];

/** Thước pH trong thẻ "Kết quả quét AI" — mọi con số nằm ở đây, không ở JSX. */
export interface MockPhGauge {
  value: string;
  unitLabel: string;
  referenceLabel: string;
  lowLabel: string;
  idealLabel: string;
  highLabel: string;
  /** Vị trí chấm trên thước, theo % chiều rộng. */
  markerPercent: number;
  statusLabel: string;
  statusTone: MockTone;
}

export interface MockWebPostPhoto {
  url: string;
  caption: string;
}

export interface MockWebPost {
  id: string;
  author: MockAuthor;
  /** Nhãn nhỏ cạnh tên (tên bé mèo / chủ đề). */
  petBadge?: string;
  /** Nhãn góc phải của thẻ. */
  cornerBadge?: string;
  cornerBadgeTone?: MockTone;
  avatarInitial: string;
  avatarTone: MockTone;
  title: string;
  body: string;
  photos: MockWebPostPhoto[];
  gauge?: MockPhGauge;
  gaugeTitle?: string;
  gaugeConfidence?: string;
  vetReply?: {
    name: string;
    badge: string;
    org: string;
    paragraphs: string[];
    helpfulLabel: string;
    replyLabel: string;
  };
  tipNote?: { title: string; body: string };
  likeCount: number;
  commentCount: number;
  /** Nhãn riêng cho nút bình luận (bài 2 ghi "Bình luận hỗ trợ"). */
  commentLabelOverride?: string;
  hasShare: boolean;
  saveLabel: string;
}

export const DESIGN_MOCK_WEB_POSTS: MockWebPost[] = [
  {
    id: "w-post-miu-alkaline",
    author: {
      name: "Mẹ Miu Miu",
      avatarUrl: avatarMeMiuMiu,
      meta: "2 giờ trước • Đã xác minh bởi Bác sĩ",
    },
    petBadge: "Bé Miu - Anh lông ngắn",
    avatarInitial: "L",
    avatarTone: "primary",
    title: "Hạt cát chuyển xanh nhạt sau 1 ngày có phải viêm bàng quang không?",
    body: "Hôm qua em thay cát chỉ thị mới cho bé Miu, sáng nay kiểm tra chỗ bé đi tiểu thấy hạt chuyển từ vàng sang màu xanh ngọc nhạt này. App quét báo mức pH 7.2 (Kiềm nhẹ). Bé vẫn ăn hạt bình thường nhưng sáng nay đi vệ sinh ngồi lâu hơn 30 giây so với mọi khi. Em lo quá không biết có nguy cơ hình thành tinh thể Struvite không ạ?",
    photos: [{ url: postLitterScanMacro, caption: "Màu xanh ngọc nhạt" }],
    gaugeTitle: "KẾT QUẢ QUÉT AI",
    gaugeConfidence: "Độ tin cậy thuật toán FelineAI: 96.8%",
    gauge: {
      value: "7.2",
      unitLabel: "pH Nước Tiểu",
      referenceLabel: "(Chuẩn: 6.0 - 6.8)",
      lowLabel: "Acid (5.5)",
      idealLabel: "Lý tưởng (6.3)",
      highLabel: "Kiềm (7.5+)",
      markerPercent: 72,
      statusLabel: "Cần chú ý",
      statusTone: "secondary",
    },
    vetReply: {
      name: "ThS. BS Lan Phương",
      badge: "Bác sĩ phản hồi",
      org: "PetCare Clinic TP.HCM • Cố vấn Feline Care CATCHECK",
      paragraphs: [
        '"Chào mẹ Miu, mức pH 7.2 hơi kiềm nhẹ. Hiện tượng này thường xảy ra khi mèo vừa ăn no (hiệu ứng alkaline tide) hoặc do khẩu phần hạt có tỷ lệ khoáng cao. Mẹ nên theo dõi thêm 24h:',
        "1. Tăng cường bát nước hoặc cho uống gel súp nước để làm loãng nước tiểu.",
        '2. Canh nếu bé có dấu hiệu rặn tiểu gián đoạn hoặc liếm bộ phận sinh dục nhiều thì nên đưa bé đi siêu âm bàng quang ngay để loại trừ sỏi Struvite sớm nhé!"',
      ],
      helpfulLabel: "Hữu ích (34)",
      replyLabel: "Phản hồi Bác sĩ",
    },
    likeCount: 48,
    commentCount: 16,
    hasShare: true,
    saveLabel: "Lưu lại",
  },
  {
    id: "w-post-switch-litter",
    author: {
      name: "Hoàng Long",
      avatarUrl: avatarHoangNam,
      meta: "5 giờ trước • Kinh nghiệm dùng cát",
    },
    petBadge: "Bé Sim & Sam",
    cornerBadge: "Thảo luận sôi nổi",
    cornerBadgeTone: "neutral",
    avatarInitial: "H",
    avatarTone: "secondary",
    title: "Kinh nghiệm đổi từ cát đất sét sang Cát Hạt Chỉ Thị CATCHECK Bio không bị lạ chân?",
    body: "Chào mọi người, bé Sim nhà mình quen dùng cát bentonite đất sét hạt mịn 2 năm nay. Tuần rồi mình muốn đổi sang dòng cát chỉ thị CATCHECK để tiện theo dõi sỏi thận nhưng bé cứ ngửi ngửi rồi nhịn tiểu không chịu vào khay. Có sen nào đã chuyển đổi thành công cho mình xin tỉ lệ trộn cát theo từng ngày với ạ?",
    photos: [],
    tipNote: {
      title: "Mẹo từ Bác sĩ:",
      body: "Quy tắc 7 ngày chuyển cát: Ngày 1-2 (75% cát cũ + 25% cát mới), Ngày 3-4 (50% - 50%), Ngày 5-6 (25% cũ - 75% mới), Ngày 7 chuyển đổi 100%.",
    },
    likeCount: 29,
    commentCount: 32,
    commentLabelOverride: "Bình luận hỗ trợ",
    hasShare: true,
    saveLabel: "Lưu",
  },
  {
    id: "w-post-oxalate",
    author: {
      name: "Minh Thảo (Bơ & Đậu)",
      avatarUrl: avatarThuTrang,
      /**
       * Bản Figma: "1 ngày trước • Đã điều trị ổn định". "điều trị" nằm trong danh sách cấm
       * REQ-COPY-01 → viết lại thành "Đã theo dõi ổn định".
       */
      meta: "1 ngày trước • Đã theo dõi ổn định",
    },
    cornerBadge: "Ca bệnh thực tế",
    cornerBadgeTone: "danger",
    avatarInitial: "B",
    avatarTone: "success",
    title: "Nhờ hạt cát đổi sang màu tím đậm phát hiện sỏi canxi oxalate kịp thời cho bé Bơ",
    /**
     * Bản Figma có hai chỗ vi phạm, đã sửa:
     *  - "(pH < 5.5 kèm vệt hồng vi thể)" → bỏ hẳn phần hồng cầu/máu (quyết định #8).
     *  - "chỉ cần truyền dịch và uống thuốc tan sỏi" → bỏ từ "thuốc", chuyển sang
     *    "theo hướng dẫn của bác sĩ tại phòng khám".
     */
    body: "Đăng bài này để cảm ơn thuật toán phát hiện màu cát của CATCHECK. Tối thứ 5 khay cát xuất hiện mảng màu tím sẫm, app quét ra pH dưới 5.5. Sáng hôm sau mình đưa Bơ đi siêu âm tại Chi cục Thú Y quận 7 thì đúng là có lắng cặn tinh thể Canxi Oxalate ở đáy bàng quang. May mắn chưa bít tắc niệu đạo hoàn toàn nên bé chỉ cần truyền dịch theo hướng dẫn của bác sĩ tại phòng khám kết hợp đổi hạt Royal Canin Urinary S/O. Các sen đừng chủ quan với màu nước tiểu nhé!",
    photos: [
      { url: postLitterPurple, caption: "Màu tím sẫm (pH < 5.5)" },
      { url: postUltrasound, caption: "Hình ảnh siêu âm" },
    ],
    likeCount: 87,
    commentCount: 41,
    hasShare: true,
    saveLabel: "Lưu",
  },
];

/** Cột phải bảng tin web — "Chủ Đề Y Khoa Tuần Này". */
export const DESIGN_MOCK_WEEKLY_TOPICS = [
  {
    tag: "#ViêmBàngQuangVoCan",
    count: "324 bài viết",
    body: "Hội chứng FIC (Feline Idiopathic Cystitis) và cách giảm căng thẳng môi trường.",
  },
  {
    tag: "#PhanTichDoKiempH",
    count: "189 bài viết",
    body: "Ý nghĩa dải màu từ 6.0 đến 7.5 trên khay cát chỉ thị sinh học.",
  },
  {
    tag: "#KhauPhanNuocUong",
    count: "256 bài viết",
    body: "Công thức tính 50ml/kg và cách kích thích mèo uống đủ nước mỗi ngày.",
  },
  {
    tag: "#DoiMauCat",
    count: "412 bài viết",
    body: "Phân biệt đổi màu do phơi khí oxy và đổi màu do phản ứng pH thật.",
  },
];

/** Cột phải bảng tin web — "Quy Chuẩn Cộng Đồng ISFM". */
export const DESIGN_MOCK_COMMUNITY_RULES = {
  intro: "Cộng đồng tuân thủ hướng dẫn Y khoa Feline thân thiện từ Hiệp hội Y học Mèo Quốc tế (ISFM):",
  alert:
    "Cảnh báo khẩn cấp: Thảo luận online và chỉ thị màu hạt cát không thay thế việc khám cấp cứu. Nếu mèo bị bí tiểu hoàn toàn quá 12h, hãy đến trạm thú y ngay lập tức!",
  items: [
    "Tôn trọng ngôn từ và không phán xét cách nuôi",
    /** Figma: "Chỉ bác sĩ có chứng chỉ mới được ghim phác đồ" → bỏ "phác đồ". */
    "Chỉ bác sĩ có chứng chỉ mới được ghim ý kiến chuyên môn",
    "Bảo vệ quyền riêng tư hồ sơ bệnh án thú y",
  ],
};

/* ====================== 3. Chi tiết thảo luận — phần dùng chung ====================== */

export const DESIGN_MOCK_THREAD = {
  id: "flutd-8821",
  breadcrumb: ["Cộng đồng y khoa", "Thảo luận lâm sàng"],
  caseCode: "Ca bệnh #FLUTD-8821",
  onlineBadge: "Đang có 1 Bác sĩ Feline trực tuyến",
  author: {
    name: "Mẹ Miu Miu",
    avatarUrl: avatarMeMiuMiu,
    roleBadge: "Chủ nuôi CATCHECK Pro",
    roleBadgeTone: "primary" as MockTone,
    meta: "Bé Miu Miu (Anh lông ngắn, 2.5 tuổi, Đực triệt sản) • Đăng 3 giờ trước",
  },
  verifiedBadge: "Đã được Bác sĩ Thú y ISFM tham vấn",
  tags: ["#FLUTD", "#DoiMauCat", "#NuocTieuKiem", "#pH7.2", "#SmartSand_Bio"],
  /** Figma tô vàng đúng nhãn này trong hàng nhãn bệnh lý (#FDCF52), các nhãn còn lại nền xanh nhạt. */
  highlightTag: "#NuocTieuKiem",
  title: "Hạt cát chuyển xanh nhạt sau 1 ngày có phải viêm bàng quang hay do thức ăn mới?",
  body: "Chào bác sĩ và cộng đồng CATCHECK. Em đổi cát chỉ thị SmartSand Bio hôm qua cho bé Miu. Sáng nay kiểm tra khay thì phát hiện vùng cát chuyển màu xanh nhạt. Ứng dụng AI quét ra kết quả pH 7.2 (Kiềm nhẹ). Bé vẫn ăn uống bình thường nhưng sáng nay đi tiểu ngồi lâu hơn 30 giây. Bác sĩ xem giúp em có nguy cơ sỏi Struvite hay viêm bàng quang FIC không ạ?",
  photo: {
    url: postLitterBluePatch,
    caption: "Ảnh chụp khay cát lúc 07:15 sáng",
    chip: "Vùng đổi màu xanh nhạt",
  },
  zoomNote: { title: "Phóng đại vùng phản ứng sinh học", meta: "Camera macro 2.4x" },
  analysis: {
    title: "Phân tích AI CATCHECK",
    confidence: "Độ tin cậy 96.8%",
    metricLabel: "CHỈ SỐ SINH HÓA",
    statusLabel: "Cảnh báo kiềm nhẹ",
    referenceLabel: "Chuẩn an toàn: 6.0 – 6.8",
    gauge: {
      value: "pH 7.2",
      unitLabel: "",
      referenceLabel: "Chuẩn an toàn: 6.0 – 6.8",
      lowLabel: "pH 5.5 (Axit)",
      idealLabel: "6.4 (Lý tưởng)",
      highLabel: "pH 8.5 (Kiềm cao)",
      markerPercent: 68,
      statusLabel: "Cảnh báo kiềm nhẹ",
      statusTone: "secondary" as MockTone,
    } satisfies MockPhGauge,
    sampleId: "Mẫu cát quét ID: #SCN-9902-14",
    algorithm: "Thuật toán VisionCare v3.1",
    lightCheck: "Mẫu quét chuẩn ánh sáng",
    chartLink: "Xem biểu đồ 7 ngày →",
  },
  likeCount: 148,
  commentCount: 36,
  shareCount: 18,
  savedNote: "Đã lưu vào sổ tay theo dõi của 42 sen khác",
};

/**
 * Khối "Ý kiến chuyên môn" của bác sĩ.
 *
 * Hai sửa đổi bắt buộc so với Figma:
 *  - Tiêu đề "Phác đồ khuyến nghị xử trí tại nhà 48 giờ tới" → "Khuyến nghị theo dõi tại
 *    nhà trong 48 giờ tới" (bỏ ngôn ngữ kê toa).
 *  - Mục 2 của Figma có "hoặc xuất hiện các chấm đỏ/nâu đậm (dấu hiệu vi thể xuất huyết
 *    bàng quang)" → BỎ HẲN theo quyết định #8 (chỉ đo pH, không phát hiện máu); tín hiệu
 *    kích hoạt chỉ còn ngưỡng pH.
 */
export const DESIGN_MOCK_VET_OPINION = {
  name: "ThS. BS. CK1 Lan Phương",
  advisorBadge: "CỐ VẤN CHUYÊN MÔN CATCHECK",
  avatarUrl: avatarVetClinic,
  org: "Chuyên khoa Tiết niệu & Nội khoa Feline • Bệnh viện PetCare Clinic (12 năm kinh nghiệm)",
  timestamp: "Phản hồi lâm sàng chính thức • Lúc 08:30 hôm nay",
  opinionBadge: "Ý KIẾN CHUYÊN MÔN",
  verifyCode: "Mã xác thực: #DOC-VN-4481",
  greeting: "Chào Mẹ Miu Miu! Cảm ơn bạn đã chủ động quan sát chỉ thị màu cát CATCHECK rất kịp thời.",
  explanation:
    "Về mặt sinh hóa, nước tiểu pH 7.2 ở mèo sau khi ăn no là hiện tượng kiềm hóa sinh lý (alkaline tide) khá phổ biến nếu bé vừa dùng thức ăn hạt có tỷ lệ tinh bột hoặc hàm lượng khoáng cao. Tuy nhiên, dấu hiệu bé ngồi khay tiểu lâu hơn 30 giây là triệu chứng báo động sớm của hội chứng đường tiết niệu dưới ở mèo (FLUTD / FIC).",
  stepsTitle: "Khuyến nghị theo dõi tại nhà trong 48 giờ tới:",
  steps: [
    {
      no: "1",
      title: "Tăng lượng nước nạp vào tối thiểu 180ml/ngày:",
      body: "Pha loãng nước luộc gà không gia vị, sử dụng đài phun nước hoặc bổ sung pate súp dinh dưỡng nhiều canh để giảm nồng độ kết tinh bàng quang.",
    },
    {
      no: "2",
      title: "Theo dõi màu cát CATCHECK mỗi lần bé đi:",
      body: "Nếu hạt cát chuyển sang màu xanh dương đậm tương ứng pH trên 7.5, hãy mang bé đến phòng khám ngay để siêu âm bàng quang và soi cặn nước tiểu loại trừ sỏi Struvite.",
    },
    {
      no: "3",
      title: "Giảm căng thẳng môi trường (Environmental Stress):",
      body: "Mèo đực rất nhạy cảm với sự thay đổi loại cát vệ sinh mới. Trộn 50% cát cũ với 50% SmartSand Bio để bé quen mùi trong 2 ngày đầu.",
    },
  ],
  helpfulLabel: "Hữu ích cho tôi (84)",
  askMoreLabel: "Đặt câu hỏi thêm",
  bookingLabel: "Đặt lịch tư vấn trực tuyến 1:1 với BS. Lan Phương",
};

/** Thẻ hồ sơ bác sĩ ở cột phải (web). */
export const DESIGN_MOCK_VET_PROFILE = {
  name: "ThS. BS. CK1 Lan Phương",
  avatarUrl: avatarVetLanPhuong,
  membership: "Thành viên Hiệp hội Feline Quốc tế (ISFM)",
  experience: "12 năm kinh nghiệm lâm sàng",
  stats: [
    { value: "1,840+", label: "Ca tư vấn", tone: "primary" as MockTone },
    { value: "99.2%", label: "Hài lòng", tone: "success" as MockTone },
    { value: "5.0 ★", label: "Đánh giá", tone: "secondary" as MockTone },
  ],
  scheduleLabel: "Lịch trực khám trực tuyến:",
  scheduleValue: "08:00 – 20:00 hàng ngày",
  clinic: "Bệnh viện Thú Y PetCare Saigon (CN Quận 2)",
  messageCta: "Nhắn tin trực tiếp với Bác sĩ",
  articlesCta: "Xem toàn bộ 142 bài viết chuyên môn",
};

/** Thẻ "Hồ sơ lâm sàng ca bệnh" ở cột phải (web). */
export const DESIGN_MOCK_CASE_PROFILE = {
  title: "HỒ SƠ LÂM SÀNG CA BỆNH",
  catBadge: "Bé Miu Miu",
  catName: "Miu Miu",
  catPhotoUrl: catMiuMiu,
  catMeta: "Anh lông ngắn • Đực (Đã triệt sản)",
  rows: [
    { label: "Cân nặng hiện tại:", value: "4.2 kg (Thể trạng chuẩn)", highlight: false },
    { label: "Thức ăn chính:", value: "Hạt Royal Canin Fit 32 + Pate", highlight: false },
    { label: "Loại cát sử dụng:", value: "SmartSand Bio-Indicator", highlight: true },
    { label: "Tần suất đi tiểu:", value: "3 lần/ngày (ngồi lâu)", highlight: false },
  ],
  historyTitle: "Lịch sử pH 7 ngày qua",
  historyToday: "Hôm nay: 7.2",
  /** `y` tính theo % chiều cao khung vẽ (0 = đỉnh). Chỉ dùng để vẽ, không phải ngưỡng. */
  historyPoints: [
    { label: "T2 (6.4)", y: 62 },
    { label: "T4 (6.3)", y: 66 },
    { label: "T6 (6.6)", y: 48 },
    { label: "CN (7.2)", y: 16 },
  ],
};

/**
 * Thẻ "Ca bệnh tương tự" ở cột phải.
 * Figma đặt tiêu đề "Ca bệnh tương tự đã khỏi" và nhãn "Đã khỏi" — "khỏi bệnh" nằm trong
 * danh sách cấm REQ-COPY-01 nên đổi sang "đã ổn định" / "Đã ổn định".
 */
export const DESIGN_MOCK_SIMILAR_CASES = {
  title: "Ca bệnh tương tự đã ổn định",
  libraryCta: "Xem thư viện 450+ bài viết tiết niệu →",
  items: [
    {
      tag: "#ViêmBàngQuangVoCan",
      badge: "Đã ổn định",
      badgeTone: "success" as MockTone,
      title: "Mèo Xiêm 3 tuổi tiểu buốt, cát đổi màu xanh lá đậm: Kinh nghiệm…",
      meta: "BS. Hoàng Hải tư vấn",
      interest: "218 Sen quan tâm",
    },
    {
      tag: "#PhanTichKiemStruvite",
      badge: "Đã ổn định",
      badgeTone: "success" as MockTone,
      title: "Chỉ số pH 7.8 liên tục 3 ngày: Siêu âm phát hiện cặn tinh thể Struvite…",
      meta: "BS. Lan Phương tư vấn",
      interest: "389 Sen quan tâm",
    },
    {
      tag: "#DoiCatChiThiDungCach",
      badge: "Hướng dẫn",
      badgeTone: "primary" as MockTone,
      title: "Quy tắc 7 ngày chuyển đổi sang SmartSand Bio mà không gây…",
      meta: "Cố vấn CATCHECK",
      interest: "512 Sen quan tâm",
    },
  ],
};

/** Thẻ "Báo động cấp cứu FLUTD" ở chân cột phải. */
export const DESIGN_MOCK_EMERGENCY_CARD = {
  title: "Báo động cấp cứu FLUTD",
  body: "Nếu bé mèo có hiện tượng rặn tiểu kêu la, liếm liên tục vùng kín hoặc không tiểu được quá 12 tiếng, đây là trường hợp tắc niệu khẩn cấp nguy hiểm tính mạng!",
  hotline: "Hotline Cứu Hộ Thú Y: 1900 8899",
};

export interface MockComment {
  id: string;
  author: MockAuthor;
  body: string;
  likeCount: number;
  reply?: { name: string; meta: string; body: string };
}

export const DESIGN_MOCK_COMMENTS: MockComment[] = [
  {
    id: "c-hoang-nam",
    author: {
      name: "Hoàng Nam (Sen bé Bơ)",
      avatarUrl: avatarHoangNamBo,
      /** Figma: "Đã từng trị sỏi Struvite" → bỏ ngôn ngữ xử lý bệnh, chuyển sang theo dõi. */
      roleBadge: "Đã từng theo dõi sỏi Struvite",
      roleBadgeTone: "secondary",
      meta: "2 giờ trước • Đã kiểm chứng bởi Bio-Kit",
    },
    body: "Bé Bơ nhà mình đợt trước cũng y chang bé Miu, đổi hạt sang dòng nhiều đạm cá hồi là cát CATCHECK báo ngay pH 7.4. Mình vội cho uống thêm nước bằng ống tiêm và đổi bát nước sang dạng đài gốm chảy tuần hoàn. 2 ngày sau cát về lại màu vàng chanh (pH 6.4) luôn. Mẹ Miu bình tĩnh làm theo lời bác sĩ Phương dặn nhé!",
    likeCount: 24,
    reply: {
      name: "Mẹ Miu Miu (Chủ thớt)",
      meta: "1 giờ trước",
      body: "Dạ cảm ơn anh Nam nhiều lắm ạ! Em vừa đi nấu nước ức gà xay loãng cho bé uống rồi, bé húp được nửa bát con. Em đang ngồi canh khay cát lần đi tiểu tiếp theo đây ạ.",
    },
  },
  {
    id: "c-thu-trang",
    author: {
      name: "Thu Trang • Sen 3 Mèo",
      avatarUrl: avatarThuTrang,
      roleBadge: "Thành viên tích cực",
      roleBadgeTone: "neutral",
      meta: "45 phút trước",
    },
    body: "Bác sĩ Lan Phương cho em hỏi ké với ạ: Với mèo đực đã triệt sản thì chỉ số pH chuẩn nhất trên cát sinh học CATCHECK nên duy trì ở ngưỡng bao nhiêu để không bị cả sỏi Canxi Oxalate (axit quá) lẫn Struvite (kiềm quá) ạ?",
    likeCount: 12,
  },
];

export const DESIGN_MOCK_COMMENTS_META = {
  countBadge: "36 trao đổi",
  sortValue: "Chuyên môn & hữu ích nhất",
  moreCta: "Xem thêm 33 phản hồi lâm sàng khác",
};

/* ================== 4. Chi tiết thảo luận — khối riêng của bản MOBILE ================== */

/**
 * Bản mobile của trang chi tiết dùng cùng một bài viết nhưng trình bày khác: một thẻ
 * "DỮ LIỆU CATCHECK THÔNG MINH" với thước pH ngang, và ghi chú chuyên môn dạng trích dẫn.
 */
export const DESIGN_MOCK_MOBILE_THREAD = {
  author: {
    name: "Minh Anh",
    avatarUrl: avatarMinhAnh,
    roleBadge: "Sen xác thực",
    roleBadgeTone: "primary" as MockTone,
    meta: "2 giờ trước",
  },
  body: [
    { kind: "text", value: "Luna vừa có chỉ số hơi kiềm (" },
    { kind: "ph", value: "pH 7.4", tone: "secondary" },
    { kind: "text", value: ") sau khi mình đổi sang pate cá hồi hôm thứ Ba. Đã trở về mức " },
    { kind: "ph", value: "pH 6.8", tone: "success" },
    {
      kind: "text",
      value:
        " lý tưởng sau khi mình bổ sung thêm vòi nước chảy tuần hoàn! Có ba mẹ nào thấy thức ăn làm đổi màu hạt cát tương tự không?",
    },
  ] satisfies MockBodySegment[],
  dataCard: {
    title: "DỮ LIỆU CATCHECK THÔNG MINH",
    statusLabel: "Cân bằng trở lại",
    shiftLabel: "Chỉ số chuyển dịch pH nước tiểu:",
    shiftFrom: "7.4",
    shiftTo: "6.8",
    lowLabel: "Toan (pH 6.0)",
    idealLabel: "Chuẩn (6.5 - 7.0)",
    highLabel: "Kiềm (pH 8.0)",
    markerPercent: 55,
  },
  photo: { url: postCatFountain, caption: "Đài phun lọc ion Luna dùng" },
  likeCount: 24,
  commentCount: 8,
  saveLabel: "14 đã lưu",
  expertNote: {
    title: "Ghi chú chuyên môn CATCH",
    subtitle: "BS. Nguyễn Lan Anh • Chuyên gia Dinh dưỡng & Tiết niệu Thú y",
    /**
     * Figma: "…đưa pH về dải an toàn 6.5–7.0, ngăn ngừa hình thành sỏi struvite." Đã đổi
     * "ngăn ngừa hình thành sỏi" (lời hứa y tế) thành "giảm nguy cơ kết tinh khoáng".
     */
    quote:
      '"Thay đổi khẩu phần ăn thường gây ra hiện tượng "thủy triều kiềm" sinh lý sau trong 4–6 giờ khi cơ thể chuyển hóa đạm. Việc kích thích uống nước tuần hoàn là giải pháp rất hữu ích giúp pha loãng nước tiểu và đưa pH về dải tham chiếu 6.5–7.0, giảm nguy cơ kết tinh khoáng struvite."',
    verifyLabel: "Tham vấn y khoa số #0829",
    /** Figma: "Xem phác đồ sỏi >" → bỏ "phác đồ". */
    linkLabel: "Xem bài viết về sỏi tiết niệu",
  },
  commentsTitle: "Bình luận cộng đồng",
  commentsCount: "(8)",
  sortValue: "Mới nhất",
  comments: [
    {
      id: "mc-hoang-nam",
      author: {
        name: "Hoàng Nam",
        avatarUrl: avatarHoangNam,
        roleBadge: "Nuôi 2 bé mèo",
        roleBadgeTone: "neutral" as MockTone,
        meta: "1 giờ trước",
      },
      body: "Nhà mình cũng từng bị vậy khi ăn hạt nhiều đạm! Cứ thấy hạt cát ngả vàng là lo, may có vòi nước lọc tự động hỗ trợ.",
      likeCount: 4,
      likeTone: "neutral" as MockTone,
    },
    {
      id: "mc-thao-le",
      author: {
        name: "Thảo Lê",
        avatarUrl: avatarThaoLe,
        roleBadge: "Bác sĩ thú y tham vấn",
        roleBadgeTone: "primary" as MockTone,
        meta: "45 phút trước",
      },
      body: "Chia sẻ rất hữu ích! Ba mẹ nhớ quét lại màu cát vào cùng một khung giờ cố định trong ngày nhé.",
      likeCount: 6,
      likeTone: "secondary" as MockTone,
    },
  ],
  composerPlaceholder: "Viết bình luận cho Minh Anh…",
};

/* ============================= 5. Màn soạn bài mới (/community/new) ============================= */

/**
 * Figma KHÔNG có frame riêng cho màn soạn bài — ô soạn nằm inline trên bảng tin web
 * (tiêu đề, nội dung, nhãn bệnh lý, ba nút đính kèm, nút "Đăng thảo luận") và bản mobile
 * chỉ có nút FAB "+ Đăng bài viết". Màn này dựng lại đúng các thành phần đó ở dạng trang
 * đầy đủ, không bịa thêm thành phần nào ngoài thiết kế.
 */
export const DESIGN_MOCK_NEW_POST = {
  categories: [
    { id: "question", label: "Hỏi đáp Bác sĩ Thú y" },
    { id: "experience", label: "Kinh nghiệm đọc màu cát" },
    { id: "nutrition", label: "Dinh dưỡng & Tiết niệu" },
    { id: "clinic", label: "Review phòng khám" },
  ],
  tags: DESIGN_MOCK_COMPOSER_TAGS,
  attachments: [
    { id: "photo", label: "Ảnh khay cát" },
    { id: "scan", label: "Kết quả quét AI" },
    { id: "record", label: "Hồ sơ khám" },
  ],
  /** Gợi ý rút từ "Quy Chuẩn Cộng Đồng ISFM" ở bảng tin web. */
  guidelines: DESIGN_MOCK_COMMUNITY_RULES.items,
};
