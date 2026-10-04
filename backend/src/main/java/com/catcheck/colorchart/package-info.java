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
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "scan::color" })
package com.catcheck.colorchart;
