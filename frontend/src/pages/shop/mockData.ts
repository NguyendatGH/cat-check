/**
 * DỮ LIỆU GIẢ (DESIGN MOCK) — KHÔNG CÓ BACKEND.
 *
 * Toàn bộ module Shop/Cart/Order hiện KHÔNG có API nào ở backend (trước đây các route
 * `/shop`, `/cart`, `/checkout`, `/orders/:orderId` đều trỏ `ComingSoonPage`). Dữ liệu dưới
 * đây chép nguyên văn từ thiết kế Figma, cả bản WEB lẫn bản MOBILE:
 *   - `16:5849` Web - 14 & Giỏ hàng (Smart Litter Shop & Cart)
 *   - `16:1592` Web - 14b. Chi tiết Sản phẩm
 *   - `16:3252` Web - Đặt hàng thành công & Theo dõi đơn hàng
 *   - `1:4066`  14. Cửa hàng Cát Thông Minh (bản mobile — bộ 5 gói Mini → Care Box)
 *   - `1:4974`  14.a Chi Tiết Sản Phẩm (bản mobile — hình thức mua, kích thước, bảng thông số)
 * dựng lên để UI khớp thiết kế, KHÔNG phải dữ liệu thật của người dùng.
 *
 * Hai bản thiết kế KHÁC NHAU về nội dung chứ không chỉ về layout (catalogue 3 sản phẩm ở web
 * vs 5 gói ở mobile), nên các hằng số dưới đây được đặt tên theo bản nguồn: `MOCK_PRODUCTS`
 * (web) và `MOCK_MOBILE_PRODUCTS` (mobile). Trang `/shop` hiển thị đúng bộ của từng breakpoint.
 *
 * Khi Phase 3 nối API thật: xoá nguyên file này và thay bằng hook gọi API — mọi nơi import
 * từ đây sẽ báo lỗi biên dịch, đó là chủ đích để không sót chỗ nào.
 *
 * ⚠️ Nội dung marketing ở đây ĐÃ LỆCH bản thiết kế một cách có chủ đích. Bản Figma (và
 * `context/spec/reference/screens/M3-...md` §14.a) viết bảng "màu cát ↔ bệnh lý" với các câu
 * hứa phát hiện máu/hồng cầu vi thể. Điều đó MÂU THUẪN quyết định #8 của owner trong
 * `context/spec/00-decisions.md` ("Chỉ số đo: Chỉ pH. Không phát hiện máu") — và theo thứ tự
 * thẩm quyền ở CLAUDE.md thì 00-decisions.md thắng, bản mockup không nằm trong chuỗi đó.
 * Đã viết lại sang ngôn ngữ quan sát pH. Cũng đã bỏ từ "thuốc"/"điều trị" vì test CI
 * REQ-COPY-01 (p17 §17.10b) quét danh sách từ cấm y tế.
 */

import heroCatLitter from "@/shared/assets/images/web-shop/hero-cat-litter.jpg";
import productSmartsandBio from "@/shared/assets/images/web-shop/product-smartsand-bio.jpg";
import productSubscription3m from "@/shared/assets/images/web-shop/product-subscription-3m.jpg";
import productCleanboxTray from "@/shared/assets/images/web-shop/product-cleanbox-tray.jpg";
import cartThumbSmartsand from "@/shared/assets/images/web-shop/cart-thumb-smartsand.jpg";
import cartThumbSpoon from "@/shared/assets/images/web-shop/cart-thumb-spoon.jpg";
import pdGalleryMain from "@/shared/assets/images/web-shop/pd-gallery-main.jpg";
import pdGallery1 from "@/shared/assets/images/web-shop/pd-gallery-1.jpg";
import pdGallery2 from "@/shared/assets/images/web-shop/pd-gallery-2.jpg";
import pdGallery3 from "@/shared/assets/images/web-shop/pd-gallery-3.jpg";
import pdGallery4 from "@/shared/assets/images/web-shop/pd-gallery-4.jpg";
import pdVetLanAnh from "@/shared/assets/images/web-shop/pd-vet-lananh.jpg";
import pdReview1 from "@/shared/assets/images/web-shop/pd-review-1.jpg";
import pdReview2 from "@/shared/assets/images/web-shop/pd-review-2.jpg";
import pdReview3 from "@/shared/assets/images/web-shop/pd-review-3.jpg";
import orderCourier from "@/shared/assets/images/web-shop/order-courier.jpg";
import orderItemSmartsand from "@/shared/assets/images/web-shop/order-item-smartsand.jpg";
import orderItemSpoon from "@/shared/assets/images/web-shop/order-item-spoon.jpg";
import orderCpoints from "@/shared/assets/images/web-shop/order-cpoints.jpg";
import orderMapHcmc from "@/shared/assets/images/web-shop/order-map-hcmc.png";
import productMini1kg from "@/shared/assets/images/web-shop/product-mini-1kg.jpg";
import productDaily25kg from "@/shared/assets/images/web-shop/product-daily-25kg.jpg";
import productPlus3kg from "@/shared/assets/images/web-shop/product-plus-3kg.jpg";
import productMulti5kg from "@/shared/assets/images/web-shop/product-multi-5kg.jpg";
import productCareBox from "@/shared/assets/images/web-shop/product-carebox.jpg";

export const SHOP_HERO_IMAGE = heroCatLitter;

export interface MockProduct {
  id: string;
  name: string;
  imageUrl: string;
  imageBadge: string;
  cornerBadge: string;
  /** Token màu cho nhãn góc — tránh hex trong .tsx (ESLint cấm). */
  cornerBadgeTone: "secondary" | "success" | "info";
  rating: number;
  ratingCount: number;
  description: string;
  price: number;
  compareAtPrice: number;
  discountPercent: number;
  /** Nhãn phụ dưới mô tả (chỉ một số sản phẩm có). */
  extraNote?: string;
  /** Giá trị của nhãn phụ, in đậm bên phải nhãn (chỉ thẻ "dải đo pH" có). */
  extraNoteValue?: string;
  /** Chọn icon + tông màu cho nhãn phụ — tránh suy đoán từ nội dung chuỗi. */
  extraNoteKind?: "gift" | "phRange" | "appScan";
  /** Nút phụ bên phải "Thêm giỏ". */
  secondaryCta: "buyNow" | "subscribe";
}

/** 3 sản phẩm đúng như bản web (KHÁC bộ 5 gói Mini/Daily/Plus/Multi/CareBox của bản mobile). */
export const MOCK_PRODUCTS: MockProduct[] = [
  {
    id: "smartsand-bio-6l",
    name: "Cát Thông Minh CATCHECK SmartSand Bio 6L",
    imageUrl: productSmartsandBio,
    imageBadge: "6 Liters (2.5kg)",
    cornerBadge: "Best Seller",
    cornerBadgeTone: "secondary",
    rating: 4.9,
    ratingCount: 1420,
    description: "Tự đổi màu quang phổ pH 5.0 - 8.5, hạt đậu nành…",
    price: 245000,
    compareAtPrice: 290000,
    discountPercent: 15,
    extraNote: "Dải đo pH",
    extraNoteValue: "Acid - Chuẩn - Kiềm",
    extraNoteKind: "phRange",
    secondaryCta: "buyNow",
  },
  {
    id: "subscription-3-months",
    name: "Gói Định Kỳ Chăm Sóc 3 Tháng",
    imageUrl: productSubscription3m,
    imageBadge: "Combo 3 Túi",
    cornerBadge: "Tiết kiệm tối ưu",
    cornerBadgeTone: "success",
    rating: 5.0,
    ratingCount: 680,
    description: "Bao gồm 3 túi SmartSand Pro 6L + Bảng đối chiếu…",
    price: 650000,
    compareAtPrice: 780000,
    discountPercent: 16,
    extraNote: "Tặng Voucher khám 100.000đ",
    extraNoteKind: "gift",
    secondaryCta: "subscribe",
  },
  {
    id: "cleanbox-tray",
    name: "Khay Cát Công Thái Học CleanBox",
    imageUrl: productCleanboxTray,
    imageBadge: "54 x 42 x 30cm",
    cornerBadge: "Khay Thông Minh",
    cornerBadgeTone: "info",
    rating: 4.8,
    ratingCount: 342,
    description: "Vách cao 30cm chống văng cát, lòng khay phủ…",
    price: 480000,
    compareAtPrice: 550000,
    discountPercent: 12,
    extraNote: "Tương thích ngàm quét CATCHECK App",
    extraNoteKind: "appScan",
    secondaryCta: "buyNow",
  },
];

/* ---------------- Cửa hàng — bản MOBILE (`1:4066`) ---------------- */

export interface MockMobileProduct {
  id: string;
  name: string;
  imageUrl: string;
  /** Pill trắng đè góc trên-trái ảnh: khối lượng gói, hoặc "Trọn gói" với combo. */
  weightBadge: string;
  /** Chip phân loại phía trên tên sản phẩm. */
  tag: string;
  tagTone: "info" | "secondary";
  subtitle: string;
  description: string;
  /** Nhãn nhỏ phía trên giá — mỗi gói một chữ khác nhau đúng bản thiết kế. */
  priceLabel: string;
  /** `true` khi nhãn giá là số tiền tiết kiệm (bản thiết kế in xanh). */
  priceLabelIsSaving?: boolean;
  price: number;
}

/**
 * 5 gói của bản mobile. KHÁC catalogue 3 sản phẩm của bản web (`MOCK_PRODUCTS`) — đây là
 * chủ đích của thiết kế chứ không phải trùng lặp dữ liệu.
 *
 * ⚠️ Hai câu mô tả đã viết lại so với bản Figma: gói Plus bản gốc ghi "Công thức trị liệu"
 * và "phát hiện vi cặn nước tiểu" — cả hai đều hứa nhiều hơn những gì sản phẩm đo được
 * (quyết định #8: chỉ pH) nên đổi sang ngôn ngữ độ nhạy pH. Xem ghi chú đầu file.
 */
export const MOCK_MOBILE_PRODUCTS: MockMobileProduct[] = [
  {
    id: "catcheck-mini-1kg",
    name: "CATCHECK Mini (1.0 kg)",
    imageUrl: productMini1kg,
    weightBadge: "1.0 kg",
    tag: "Lựa chọn thử nghiệm",
    tagTone: "info",
    subtitle: "Lựa chọn thử nghiệm (Dùng khoảng 10 ngày)",
    description: "Gói khởi đầu cho 1 mèo theo dõi pH tức thì với hạt chỉ thị màu vi sinh.",
    priceLabel: "Giá bán",
    price: 89000,
  },
  {
    id: "catcheck-daily-25kg",
    name: "CATCHECK Daily (2.5 kg)",
    imageUrl: productDaily25kg,
    weightBadge: "2.5 kg",
    tag: "Phổ biến nhất",
    tagTone: "secondary",
    subtitle: "Gói tiêu chuẩn dùng 1 tháng",
    description: "Bentonite tự nhiên kết hợp hạt silica chỉ thị pH. 99.9% không bụi.",
    priceLabel: "Gói tiêu chuẩn",
    price: 179000,
  },
  {
    id: "catcheck-plus-3kg",
    name: "CATCHECK Plus (3.0 kg)",
    imageUrl: productPlus3kg,
    weightBadge: "3.0 kg",
    tag: "Mèo nhạy cảm",
    tagTone: "info",
    subtitle: "Công thức tăng cường",
    description: "Độ nhạy chỉ thị pH nâng cao, hợp với mèo có hệ tiết niệu nhạy cảm.",
    priceLabel: "Công thức tăng cường",
    price: 299000,
  },
  {
    id: "catcheck-multi-5kg",
    name: "CATCHECK Multi (5.0 kg)",
    imageUrl: productMulti5kg,
    weightBadge: "5.0 kg",
    tag: "Nhà nhiều mèo",
    tagTone: "info",
    subtitle: "Gói siêu bền",
    description: "Kéo dài thời gian sử dụng cho gia đình nuôi từ 2 mèo trở lên với hạt bền.",
    priceLabel: "Gói tiết kiệm",
    price: 349000,
  },
  {
    id: "catcheck-care-box",
    name: "CATCHECK Care Box",
    imageUrl: productCareBox,
    weightBadge: "Trọn gói",
    tag: "Combo Trọn gói Giá trị",
    tagTone: "secondary",
    subtitle: "Bộ chăm sóc toàn diện",
    description: "2 bao cát 2.5kg + Thẻ thước đo màu quang học + Xẻng lọc chuyên dụng.",
    priceLabel: "Tiết kiệm 78.000 đ",
    priceLabelIsSaving: true,
    price: 499000,
  },
];

/** Khối "Cam kết giao hàng CATCHECK" — chỉ có ở bản mobile của trang Cửa hàng. */
export const MOCK_DELIVERY_COMMITMENTS = [
  {
    key: "fast",
    title: "Giao hàng nhanh toàn quốc",
    body: "Hà Nội & TP.HCM giao hỏa tốc 2 giờ. Toàn quốc 1-2 ngày.",
  },
  {
    key: "genuine",
    title: "100% Chính hãng",
    body: "Đổi mới miễn phí ngay lập tức nếu bao bì bị rách hoặc ẩm ướt.",
  },
  {
    key: "clinic",
    title: "Đạt chứng nhận Phòng khám Thú y",
    body: "Đối tác phân phối chính thức tại hơn 120 phòng khám trên cả nước.",
  },
] as const;

export interface MockCartLine {
  id: string;
  name: string;
  subtitle: string;
  imageUrl: string;
  unitPrice: number;
  /** Quà tặng: giá 0đ, hiển thị giá gốc gạch ngang, không đổi số lượng. */
  isGift?: boolean;
  compareAtPrice?: number;
  quantity: number;
}

export const MOCK_CART_LINES: MockCartLine[] = [
  {
    id: "line-smartsand",
    name: "Cát SmartSand Bio 6L",
    subtitle: "Hương đậu nành nguyên bản",
    imageUrl: cartThumbSmartsand,
    unitPrice: 245000,
    quantity: 2,
  },
  {
    id: "line-spoon-gift",
    name: "Muỗng xúc đo quang phổ 7 màu",
    subtitle: "Quà tặng 0đ kèm đơn",
    imageUrl: cartThumbSpoon,
    unitPrice: 0,
    compareAtPrice: 45000,
    isGift: true,
    quantity: 1,
  },
];

export const MOCK_VOUCHER = {
  code: "CHAO_SEN",
  note: "(-15% gói đầu)",
  discountAmount: 73500,
};

export const MOCK_SHIPPING_LABEL = "Miễn phí (2H)";
export const MOCK_FREE_SHIP_PROGRESS = "Đạt chuẩn Miễn Phí Vận Chuyển Hỏa Tốc 2H";

/* ------- Màn giỏ hàng riêng, bản mobile `Giỏ hàng (Shopping Cart)` (450×1949) -------
 *
 * Bản WEB gộp giỏ hàng thành cột phải của trang Cửa hàng (xem `CartPanel`) nên nó KHÔNG có
 * các khối dưới đây. Route `/cart` dựng theo bản mobile — đầy đủ địa chỉ giao, đồng bộ hồ sơ
 * mèo, chọn phương thức thanh toán — rồi mở rộng lên 2 cột ở `lg`. Vẫn là dữ liệu mock.
 */

export const MOCK_DELIVERY_ADDRESS = {
  receiverName: "Trương Thị Huyền",
  /** Đã che theo đúng thiết kế — p15 cấm phơi số điện thoại đầy đủ trên UI tổng quan. */
  phoneMasked: "0912 ••• 888",
  line: "14 Nguyễn Văn Hưởng, P. Thảo Điền, TP. Thủ Đức, TP.HCM",
  speedBadge: "Giao nhanh Hỏa tốc 2H",
};

export const MOCK_CART_CAT_SYNC = {
  catName: "Luna",
  breedName: "British Shorthair",
};

export type MockPaymentMethodId = "momo" | "vietqr" | "cod" | "card";

export interface MockPaymentMethod {
  id: MockPaymentMethodId;
  name: string;
  note: string;
  recommended?: boolean;
}

export const MOCK_PAYMENT_METHODS: MockPaymentMethod[] = [
  { id: "momo", name: "Ví MoMo", note: "Xác thực FaceID 1 chạm", recommended: true },
  { id: "vietqr", name: "Quét mã VietQR", note: "Chuyển khoản tức thì mọi ngân hàng" },
  { id: "cod", name: "Thanh toán khi nhận hàng (COD)", note: "Kiểm tra kiện hàng trước khi thanh toán" },
  { id: "card", name: "Thẻ Quốc tế (Visa, Mastercard)", note: "Bảo mật chuẩn quốc tế 3D Secure" },
];

/**
 * Ba nhãn tin cậy cuối trang. Câu chữ marketing của chính bản thiết kế — KHÔNG phải mô tả
 * tính năng đã có ở backend (chưa có kênh "Bác sĩ 24/7" nào trong spec).
 */
export const MOCK_CART_TRUST = [
  { key: "verified", title: "100% Kiểm định", note: "Chuẩn Thú y" },
  { key: "refund", title: "Đổi trả miễn phí", note: "Trong 30 ngày" },
  { key: "vet", title: "Bác sĩ 24/7", note: "Đọc kết quả scan" },
] as const;

/** Số voucher khả dụng hiển thị ở "Xem tất cả (4)" — danh sách đầy đủ chưa có màn nào. */
export const MOCK_VOUCHER_AVAILABLE_COUNT = 4;

/* ---------------- Trang chi tiết sản phẩm (16:1592) ---------------- */

export const MOCK_PRODUCT_DETAIL = {
  id: "smartsand-bio-6l",
  breadcrumb: ["Cửa hàng Cát", "Cát chỉ thị sức khỏe", "CATCHECK SmartSand Pro™ 6L"],
  title: "Cát Chỉ Thị Sức Khỏe Sinh Học CATCHECK SmartSand Pro™ 6L",
  description:
    "Công nghệ quang phổ vi sinh độc quyền giúp nhận diện sớm biến đổi pH, sỏi thận Struvite, Canxi Oxalate và hội chứng đường tiết niệu dưới (FLUTD) trước khi mèo có triệu chứng đau đớn.",
  topBadges: ["Thế hệ Bio-Litter 4.0", "Giao hỏa tốc 2H HCM & HN"],
  imageBadge: "ISFM Accredited Feline Formula",
  rating: 4.9,
  ratingCount: 1420,
  ratingNote: "(1.420 đánh giá thực tế)",
  clinicNote: "120+ Phòng khám Thú Y khuyên dùng",
  price: 245000,
  compareAtPrice: 290000,
  discountLabel: "Tiết kiệm 15%",
  shipNote: "Freeship toàn quốc đơn từ 2 túi",
  priceFootnote: "Giá đã bao gồm VAT & Mã bảo hiểm đổi trả 30 ngày",
  /** `[0]` là ảnh lớn; 4 phần tử sau là 4 thumbnail đúng bản web (ảnh cuối: giấy kiểm nghiệm). */
  gallery: [pdGalleryMain, pdGallery1, pdGallery2, pdGallery3, pdGallery4],
  indicatorStrip: "CHỈ SỐ PHẢN ỨNG MÀU TRỰC TIẾP",
  indicatorStripNote: "Phát hiện ngay sau 5-15 phút",
  consultTitle: "Bạn cần giải đáp về kết quả đổi màu cát của bé mèo?",
  consultBody: "Bác Sĩ Thú y ISFM sẵn sàng xem ảnh quét và tư vấn miễn phí ngay lúc này.",
  consultBadge: "Trực tuyến",
  comboLabel: "Lựa chọn combo tiết kiệm:",
  comboOption: {
    name: "Khay Vệ Sinh CleanBox™ Combo",
    detail: "Gồm 1 Khay kháng khuẩn gốc nghiêng công thái học + 2 Túi SmartSand",
    price: 589000,
  },
  subscribeNote: "Đăng ký giao định kỳ tự động mỗi tháng",
  subscribeSaving: "Giảm thêm 10% trọn đời",
  specs: [
    { title: "100% Đậu Nành", body: "Hữu cơ tự nhiên" },
    { title: "Tan Bón Cầu", body: "Phân hủy sinh học" },
    { title: "Khử Mùi 99%", body: "Khóa Amoniac tức thì" },
    { title: "Không Bụi 99.9%", body: "Bảo vệ hô hấp mèo" },
  ],
  mechanismEyebrow: "CHỨNG THỰC LÂM SÀNG FELINE BIO-TECH",
  mechanismTitle: "Cơ chế phát hiện bệnh thông qua màu cát",
  mechanismRange: "Dải chỉ thị Bio-Indicator: pH 5.0 - pH 8.5",
  steps: [
    {
      no: "01",
      title: "Rải đều cát",
      body: "Rải một lớp CATCHECK SmartSand Pro dày từ 5-7cm vào khay vệ sinh sạch và khô ráo.",
    },
    {
      no: "02",
      title: "Bé mèo đi vệ sinh",
      body: "Để mèo đi tiểu tự nhiên. Hạt bio-reagent sẽ tức thì phản ứng hóa sinh với nước tiểu trong 5-15 phút.",
    },
    {
      no: "03",
      title: "Mở App CATCHECK",
      body: 'Chọn chức năng "Quét màu cát". Camera AI tự động bù trừ ánh sáng và đối chiếu biểu đồ chuẩn ISO.',
    },
    {
      no: "04",
      title: "Đồng bộ hồ sơ số",
      body: "Kết quả tự động cập nhật vào bệnh án của bé, sẵn sàng chia sẻ trực tiếp với bác sĩ thú y khi cần.",
    },
  ],
  comparisonHeaders: [
    "Quyền lợi & Tính năng",
    "Mua lẻ 1 Túi (6L)",
    "Gói Định Kỳ 3 Tháng (Khuyên dùng)",
    "CleanBox Combo",
  ],
  comparisonRows: [
    { label: "Giá mỗi túi 6L tương đương", values: ["245.000đ", "216.000đ (Tiết kiệm 25%)", "230.000đ"] },
    { label: "Tặng kèm Muỗng đo quang phổ chuẩn", values: ["—", "yes", "yes"] },
    { label: "Voucher khám thú y liên kết 100K", values: ["—", "yes", "—"] },
    { label: "Tự động nhắc đổi cát trên App", values: ["yes", "yes", "yes"] },
    {
      label: "Hỗ trợ Bác sĩ Thú y phân tích miễn phí",
      values: ["1 lần / tháng", "Không giới hạn 24/7", "3 lần / quý"],
    },
  ],
  reviewsEyebrow: "TRẢI NGHIỆM THỰC TẾ TỪ CỘNG ĐỒNG SEN",
  reviewsTitle: "Đánh giá & Ảnh quét thực tế (1.420)",
  reviewsScore: "4.9",
  reviewsScoreNote: "98.5% hài lòng tuyệt đối",
};

/* ------- Chi tiết sản phẩm — các khối CHỈ CÓ ở bản mobile (`1:4974`) -------
 *
 * Bản web `16:1592` không có "Hình thức mua hàng", "Kích thước & Công thức", bảng
 * "Tiêu chuẩn công thức" và thẻ trích dẫn bác sĩ; bản mobile không có khối combo CleanBox.
 * Nên các khối dưới đây chỉ render dưới `lg`.
 */

export const MOCK_PURCHASE_MODES = [
  {
    id: "subscription",
    title: "Giao định kỳ & Tiết kiệm 10%",
    body: "Giao tự động mỗi 4 tuần. Không bao giờ lo gián đoạn việc theo dõi sức khỏe. Hủy hoặc tạm dừng bất cứ lúc nào.",
    price: 161100,
    priceUnit: "/ chu kỳ",
    badge: "Khuyên dùng",
  },
  {
    id: "one-time",
    title: "Mua một lần",
    body: "Giao thử nghiệm 1 bao cát. Áp dụng phí vận chuyển tiêu chuẩn.",
    price: 179000,
    priceUnit: "",
    badge: null,
  },
] as const;

/** 4 kích thước gói — cùng bộ số với `MOCK_MOBILE_PRODUCTS` (trừ Care Box). */
export const MOCK_PACK_SIZES = [
  { id: "trial", title: "Dùng thử", detail: "1.0 kg • 7-10 Ngày", price: 89000 },
  { id: "standard", title: "Tiêu chuẩn", detail: "2.5 kg • 30 Ngày", price: 179000 },
  { id: "enhanced", title: "Bảo vệ tăng cường", detail: "3.0 kg • Than hoạt tính kép", price: 299000 },
  { id: "multi", title: "Nhà nhiều mèo", detail: "5.0 kg • Chịu lực cao", price: 349000 },
] as const;

export const MOCK_SPEC_TABLE = [
  { label: "Thành phần hoạt tính", value: "85% Bentonite, 15% Silica chỉ thị pH", tone: "default" as const },
  { label: "Khử mùi", value: "Than hoạt tính + Men vi sinh tự nhiên", tone: "default" as const },
  { label: "Độ sạch bụi", value: "99.9% Không bụi (Quy trình hút bụi 5 lần)", tone: "success" as const },
  { label: "Độ sâu khuyến nghị", value: "7 - 8 cm trong khay cát", tone: "default" as const },
];

/**
 * Trích dẫn bác sĩ ở cuối bản mobile. Câu gốc trong Figma nói về việc nhận biết thay đổi pH
 * sớm — giữ nguyên ý đó, KHÔNG thêm cam kết phát hiện bệnh.
 */
export const MOCK_VET_QUOTE = {
  name: "BS. Lan Anh, DVM",
  org: "Hội Thú y Mèo Sài Gòn",
  photoUrl: pdVetLanAnh,
  quote:
    "Bệnh đường tiết niệu dưới ở mèo thường tiến triển âm thầm. CATCHECK giúp các chủ nuôi nhận biết sự thay đổi pH nước tiểu vài ngày trước khi tinh thể kết tụ gây tắc nghẽn nguy hiểm.",
  cta: "Xem báo cáo kiểm nghiệm lâm sàng",
};

/**
 * Bảng "màu cát ↔ tình trạng" — nguyên văn thiết kế. Xem cảnh báo mâu thuẫn ở đầu file:
 * mục "Màu Đỏ Gạch / Cam" đã viết lại theo quyết định #8 — xem ghi chú đầu file.
 */
export const MOCK_COLOR_INDICATORS = [
  {
    tone: "success" as const,
    statusLabel: "Bình thường",
    name: "Màu Xanh Lục",
    range: "Khoảng pH 6.2 - 7.0",
    body: "Nước tiểu ở ngưỡng sinh lý tối ưu. Hệ vi sinh bàng quang và thận của mèo hoạt động ổn định, không có cặn lắng khoáng chất.",
    footer: "Duy trì chế độ ăn hiện tại",
  },
  {
    tone: "primary" as const,
    statusLabel: "Cảnh báo Kiềm",
    name: "Màu Xanh Lam Đậm",
    range: "Khoảng pH 7.5 - 8.5",
    body: "Nghi cơ kiềm hóa nước tiểu do nhiễm khuẩn đường tiết niệu (UTI) hoặc nguy cơ hình thành tinh thể sỏi khoáng Struvite nguy hiểm.",
    footer: "Tăng cường nước & thức ăn ướt",
  },
  {
    tone: "warning" as const,
    statusLabel: "Cảnh báo Toan",
    name: "Màu Tím Sẫm",
    range: "Khoảng pH < 6.0",
    body: "Môi trường quá toan tính (acidic). Tăng nguy cơ kết tủa tinh thể Canxi Oxalate ở bàng quang và thận.",
    footer: "Cần xét nghiệm nước tiểu thú y",
  },
  {
    tone: "danger" as const,
    statusLabel: "Báo động đỏ",
    name: "Màu Đỏ Gạch / Cam",
    range: "Màu lệch bất thường",
    body: "Màu cát lệch khỏi mọi dải pH tham chiếu. CatCheck không kết luận được nguyên nhân — đây là tín hiệu cần đưa bé đi khám sớm.",
    footer: "Liên hệ Bác sĩ thú y khẩn cấp",
  },
];

export const MOCK_REVIEWS = [
  {
    id: "r1",
    initials: "TH",
    author: "Trần Hoàng Bảo",
    meta: "Đã mua gói & Quét thử nghiệm",
    time: "2 ngày trước",
    body: "Bé Miu nhà mình từng bị bí tiểu FLUTD 1 lần tốn gần chục triệu chữa. Đổi qua CATCHECK SmartSand này 2 tuần trước thì thấy hạt chuyển xanh lam đậm, mở app quét ra cảnh báo kiểm pH 7.8. Mình mang ngay ra viện thì bác sĩ bảo vừa chớm kết tinh sỏi Struvite, xử lý kịp thời luôn!",
    imageUrl: pdReview1,
    caption: "Bác sĩ Thú y CATCHECK xác nhận ca can thiệp sớm thành công.",
  },
  {
    id: "r2",
    initials: "NL",
    author: "Ngọc Lan (Sen 3 Mèo Anh)",
    meta: "Đã mua gói 3 tháng",
    time: "5 ngày trước",
    body: "Cát đậu nành cực kỳ thơm thoang thoảng mùi sữa chua tự nhiên, khử mùi siêu đỉnh so với các loại cát đất sét cũ. Hạt chỉ thị màu sắc rất rõ rệt và app quét nhận diện màu chỉ mất 2 giây. Đặc biệt là xả thẳng bồn cầu tan veo không hề tắc.",
    imageUrl: pdReview2,
    caption: "Kết quả: pH 6.6 Hoàn toàn khỏe mạnh",
  },
  {
    id: "r3",
    initials: "DR",
    author: "BS. CKI Lê Quang Vinh",
    meta: "BV Thú Y PetHealth Sài Gòn",
    time: "1 tuần trước",
    body: "Mèo là loài giỏi che giấu cơn đau đường tiết niệu cho đến khi tắc niệu đạo hoàn toàn. Việc theo dõi màu cát sinh học hàng ngày tại nhà như SmartSand Pro giúp người nuôi nhận ra thay đổi pH sớm và đưa bé đi khám đúng lúc.",
    imageUrl: pdReview3,
    caption: "Chứng nhận chuyên môn Thú Y Cấp Cao ISFM",
  },
];

/* ---------------- Trang theo dõi đơn hàng (16:3252) ---------------- */

export const MOCK_ORDER = {
  code: "#CCK-2026-89412",
  placedAtLabel: "Hôm nay, 14:20",
  paymentLabel: "VietQR (Đã thanh toán)",
  etaLabel: "16:30 Hôm nay (25 phút nữa)",
  slaBadge: "Giao Hỏa Tốc 2H Nội Thành",
  thanksBody:
    "Cảm ơn bạn đã tin chọn CATCHECK SmartSand Pro để chăm sóc và bảo vệ sức khỏe hệ bài tiết bé mèo của bạn một cách chủ động.",
  courierDistance: "Tài xế đang cách bạn 2.4 km",
  trackingSubtitle: "Lộ trình hỏa tốc được định vị GPS thời gian thực từ Bio-Center CATCHECK",
  steps: [
    { title: "1. Xác nhận đơn hàng", time: "14:20 · Hệ thống AI duyệt", state: "done" as const },
    { title: "2. Hiệu chuẩn & Đóng gói", time: "14:35 · Đã niêm phong hút chân không", state: "done" as const },
    { title: "3. Đang giao hỏa tốc", time: "Đang di chuyển · Còn ~25 phút", state: "current" as const },
    { title: "4. Nhận hàng thành công", time: "Dự kiến 16:30", state: "pending" as const },
  ],
  courier: {
    teamLabel: "Đội ngũ giao hỏa tốc CATCHECK",
    name: "Nguyễn Văn An",
    role: "Chuyên viên Vận chuyển Sinh học",
    ratingLabel: "5.0 (612 chuyến chuẩn xác)",
    vehicle: "Honda Lead Trắng (59-X1 889.21)",
    coolingLabel: "Kiểm soát ẩm <45% RH",
    departedAt: "14:40 · Thảo Điền Hub",
    photoUrl: orderCourier,
    speedLabel: "Tốc độ: 32 km/h",
    driverLabel: "Tài xế Nguyễn Văn An (Lead 59-X1 889.21)",
    destinationLabel: "Số 42 Đường số 12, P. Thảo Điền, TP. Thủ Đức",
    etaShort: "~25 phút",
  },
  packageLabel: "Thùng đóng gói Bio-Box #1",
  items: [
    {
      id: "o1",
      name: "Cát Chỉ Thị CATCHECK SmartSand Pro 6L",
      subtitle: "Công nghệ hạt silica đổi màu sinh học…",
      qtyLabel: "Số lượng: 02 gói (12L tổng)",
      note: "Đã kiểm định độ nhạy pH 6.0 - 8.0",
      price: 490000,
      imageUrl: orderItemSmartsand,
    },
    {
      id: "o2",
      name: "Quà Tặng: Muỗng Xúc Đo Quang Phổ Calibrated",
      subtitle: "Tích hợp thước màu chuẩn độ phân giải cao…",
      qtyLabel: "Số lượng: 01 chiếc",
      note: "Kèm mã QR kích hoạt AI Care",
      price: 0,
      compareAtPrice: 85000,
      isGift: true,
      imageUrl: orderItemSpoon,
    },
  ],
  subtotal: 490000,
  voucherLabel: "Mã giảm giá thành viên mới (CHAO_SEN):",
  voucherAmount: 73500,
  voucherPercent: "(15%)",
  shippingLabel: "Miễn phí (Trợ giá CatCheck)",
  total: 416500,
  vatNote: "Đã bao gồm thuế GTGT (VAT 8%)",
  recipient: { name: "Anna Nguyễn · 0988 234 567", address: "Số 42 Đường số 12, P. Thảo Điền, TP. Thủ Đức" },
  driverNote: "Ghi chú gửi tài xế: Giao sảnh lễ tân chung cư, gọi trước 10 phút.",
  prepTitle: "Chuẩn bị trước khi nhận cát mới",
  prepSubtitle: "Quy chuẩn phòng lab CATCHECK Lab",
  prepIntro:
    "Để các hạt chỉ thị quang phổ nhận diện chính xác 100% các biến đổi vi lượng trong nước tiểu mèo, vui lòng chuẩn bị khay vệ sinh theo 3 bước sau:",
  prepSteps: [
    {
      title: "Loại bỏ sạch hoàn toàn cát cũ",
      body: "Không trộn lẫn CATCHECK SmartSand Pro với các loại cát đất sét bentonite hoặc đậu nành thông thường.",
    },
    {
      title: "Rửa khay bằng nước ấm & xà phòng nhẹ",
      body: "Tránh dùng chất tẩy clo đậm đặc vì có thể làm sai lệch dải màu phản ứng pH của cát mới.",
    },
    {
      title: "Lau khô hoàn toàn trước khi đổ cát",
      body: "Đổ độ dày tối ưu 5 - 7 cm để đảm bảo độ thẩm hút 1 chiều và hiển thị vệt màu chuẩn nhất.",
    },
  ],
  pointsTitle: "Tích lũy +416 C-Points",
  pointsBody: "Đã cộng vào Hồ sơ sức khỏe bé Luna. Sử dụng để đổi quà hoặc tư vấn thú y.",
  pointsImageUrl: orderCpoints,
  insuranceEyebrow: "QUYỀN LỢI KÈM THEO ĐƠN HÀNG",
  insuranceTitle: "Gói Bảo Hiểm Sinh Học 30 Ngày",
  insuranceBody:
    "Nếu kết quả quét cho thấy pH lệch khỏi dải tham chiếu nhiều lần liên tiếp, CATCHECK sẽ gửi cảnh báo để bạn cân nhắc đưa bé đi khám.",
  insuranceCta: "Kích hoạt theo dõi khay cát cho bé Luna",
  insuranceSupport: "Cần hỗ trợ đơn hàng? Chat ngay với hỗ trợ viên 24/7",
};

/**
 * Ảnh bản đồ tĩnh của khu vực giao hàng, cắt từ chính file Figma (`16:3252`). Đây là ảnh
 * chụp màn hình trong bản mockup, KHÔNG phải map provider — stack chưa chốt nhà cung cấp
 * bản đồ nào, nên khi Phase 3 nối map thật thì thay cả ảnh này.
 */
export const MOCK_ORDER_MAP_IMAGE = orderMapHcmc;

/** Định dạng tiền VND theo kiểu hiển thị trong thiết kế: "245.000đ". */
export function formatVnd(value: number): string {
  return `${value.toLocaleString("vi-VN")}đ`;
}
