/**
 * Lõi khoa màu thuần Java của CatCheck — trái tim kỹ thuật của sản phẩm.
 *
 * <h2>Vì sao package này phải thuần Java</h2>
 * <p>Toàn bộ thuật toán màu nằm ở đây: sRGB↔CIELAB (D65/2°), ΔE2000 (kL=2, kC=1, kH=1), gray-ramp
 * linearization, CCM 3×3 bằng weighted least squares, cân bằng trắng, thống kê robust theo MAD,
 * khớp bảng màu, tính confidence + khoảng tin cậy, phân loại dải pH.
 *
 * <p><b>R9 (ArchUnit) cấm tuyệt đối</b> mọi phụ thuộc vào {@code org.bytedeco..},
 * {@code org.springframework..} và {@code jakarta..} trong {@code com.catcheck.scan.domain.color..}.
 * OpenCV chỉ được xuất hiện ở {@code com.catcheck.scan.infrastructure.vision}.
 *
 * <p>Ba lý do cụ thể, không phải lý do thẩm mỹ:
 * <ol>
 *   <li><b>Lưu được tham số vào DB.</b> Backfill S8–S10 (p6 §6.6.5) phải tính lại pH từ
 *       {@code lab_l/a/b} đã lưu mà không cần ảnh gốc ⇒ mọi tham số (hệ số linearization,
 *       ma trận CCM, white-balance gains) phải serialize được, không được nằm trong state
 *       native của OpenCV.</li>
 *   <li><b>Unit-test được trong CI không cần native.</b> OpenCV cần glibc + vài trăm MB artifact.
 *       Toàn bộ tầng 1 của bộ test p6 §6.13.2 (ΔE2000, ColorSpace, CcmSolver, ChartMatcher,
 *       Confidence, RobustStats, ClassificationBand) là "Không cần native" — điều đó chỉ đúng
 *       khi toán nằm ở đây.</li>
 *   <li><b>Có đường suy giảm khi native lỗi.</b> Khi {@code Loader.load()} thất bại (thiếu glibc,
 *       sai classifier) thì phần đo màu vẫn chạy được qua nhánh S4b, chỉ mất độ chính xác.</li>
 * </ol>
 *
 * <h2>Cấu trúc</h2>
 * <ul>
 *   <li>Kiểu giá trị &amp; toán: {@code Lab}, {@code RgbLinear}, {@code Xyz}, {@code Matrix3},
 *       {@code ColorSpace}, {@code DeltaE2000}, {@code RobustStats}.</li>
 *   <li>Hiệu chuẩn: {@code CcmSolver}, {@code LinearizationCurve}, {@code SubstrateWhiteBalance},
 *       {@code CardCalibration} (S4 + S4b).</li>
 *   <li>Khớp bảng màu &amp; kết luận: {@code PhChart}, {@code ChartMatcher}, {@code ConfidenceCalculator},
 *       {@code PhBandModel} (S8, S9, S10).</li>
 *   <li>Cổng (port) cho tầng OpenCV: package {@code port} — khai báo ở đây, hiện thực ở
 *       {@code com.catcheck.scan.infrastructure.vision}.</li>
 * </ul>
 *
 * <p>Nguồn đặc tả: p6 §6.5 (S3–S9), p6 §6.7–6.8, p4 §4.5 (I9, I21, I22), research-color-pipeline
 * mục (c) và (d).
 *
 * <p>The package is exposed as the narrow {@code scan::color} Modulith interface because
 * {@code colorchart} supplies {@code ChartCatalog} and validates published charts with the same
 * ΔE00 implementation. The annotation lives only in package metadata; every algorithm class
 * remains independent of Spring and Jakarta.</p>
 */
@org.springframework.modulith.NamedInterface("color")
package com.catcheck.scan.domain.color;
