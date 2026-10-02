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
 * <p><b>Khiếm khuyết hợp đồng đã biết (chưa sửa):</b> {@code scan.domain.color} không thể mang
 * {@code @NamedInterface} (R9 cấm mọi annotation Spring trong package đó — xem
 * {@code scan/domain/color/package-info.java}), nên KHÔNG có cách khai {@code allowedDependencies}
 * nào ở đây khiến {@code ModularityTests} hết đỏ ngoài để bare {@code "scan"} (chỉ hợp thức phần
 * "unnamed" của scan, không hợp thức {@code domain.color}) hoặc {@code type = OPEN} cho cả module
 * {@code scan} (quá rộng, không nên). Nợ kiến trúc thật, cần tách {@code scan.domain.color} ra
 * khỏi module {@code scan} mới giải quyết gốc — xem javadoc bên đó.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "scan" })
package com.catcheck.colorchart;
