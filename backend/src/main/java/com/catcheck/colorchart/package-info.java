/**
 * Bảng màu pH (color_chart) + dải phân loại (ph_classification_band) + layout thẻ tham chiếu
 * + job hiệu chuẩn + màu cát nền.
 *
 * <h2>Chiều phụ thuộc: colorchart → scan</h2>
 * <p>Module này dùng {@code scan.domain.color} ({@code DeltaE2000}, {@code Lab},
 * {@code DeltaE2000Params}) để validate publish (ΔE00 ≥ 3 giữa các mức liền kề, p6 §6.6.2) và để
 * hiện thực cổng {@code ChartCatalog} mà scan tuyên bố. Module {@code scan} <b>không</b> phụ thuộc
 * module này — nó chỉ biết interface {@code ChartCatalog}, do đó không có phụ thuộc vòng.
 *
 * <p>Đảo ngược phụ thuộc bằng cách scan tuyên bố cổng cần và colorchart hiện thực — đúng chuẩn
 * dependency inversion, và ArchUnit không thấy cạnh nào đi tới colorchart từ scan.
 *
 * <p>The dependency is restricted to the {@code scan::color} named interface; the remainder of
 * the scan module stays internal.</p>
 *
 * <h2>Hai cạnh thêm ở W5-B (L33–L35, L38–L39)</h2>
 * <p>{@code scan::api} — backfill bảng màu ghi {@code scan_analysis} và
 * {@code scan_analysis_recompute}, hai bảng do module {@code scan} sở hữu (p4 D3/D4), nên phép
 * tính lại nằm sau cổng {@code scan.api.ChartBackfill} thay vì để colorchart chạy SQL trên bảng
 * của module khác (chỗ lệch mà handoff H15.103 đã ghi, và ArchUnit không bắt được vì ranh giới
 * Modulith tính theo type Java).</p>
 * <p>{@code audit::api} — L33/L35 mang ký hiệu {@code Aud} + {@code Rsn} (p8 §8.4.12): publish
 * một bảng màu và tính lại kết quả cũ của toàn bộ người dùng là hành động phải truy ngược được.</p>
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = { "shared", "scan::color", "scan::api", "audit::api" })
package com.catcheck.colorchart;
