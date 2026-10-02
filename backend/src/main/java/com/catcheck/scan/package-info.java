/**
 * Quét ảnh cát vệ sinh, chạy pipeline nhận diện màu -> ước lượng pH (scan_analysis).
 *
 * <p>Chiều phụ thuộc thắt chặt qua named interface (R6) ở nơi làm được: {@code media::api} (chỉ
 * {@code ImageStorage}), {@code audit::api} (ghi audit_log), {@code credit::api} (chỉ
 * {@code CreditConsumption}/{@code EntitlementQuery}/{@code CreditErrorCode}). Kiểm tra thật bằng
 * {@code ModularityTests} xác nhận Spring Modulith coi bare {@code "credit"} và
 * {@code "credit::api"} là HAI mức khác nhau — chỉ khai "credit::api" mới hết bị báo vi phạm
 * truy cập vào chính named interface đó (giả định ban đầu "bare = rộng hơn" là SAI, đã sửa theo
 * kết quả chạy thật, không suy đoán). Vẫn còn ĐÚNG MỘT loại vi phạm không tránh được dù khai kiểu
 * gì: {@code credit.api.CreditConsumption.CreditConsumeCommand} đòi tham số kiểu
 * {@code credit.domain.CreditLedgerRefType}, và {@code credit.api.EntitlementQuery} đòi
 * {@code credit.domain.PlanFeature} — cả hai type KHÔNG nằm trong named interface "api" của
 * credit. Đây là khiếm khuyết trong hợp đồng công khai của module credit (A4), không phải của
 * scan — xem {@code docs/handovers/A6.md}. Cổng
 * {@code scan.domain.color.port.ChartCatalog} do CHÍNH module này khai báo và module
 * {@code colorchart} hiện thực (đảo chiều phụ thuộc — xem {@code colorchart/package-info.java}),
 * nên {@code scan} KHÔNG cần khai "colorchart" trong danh sách này dù có dùng dữ liệu bảng màu.
 *
 * <p>Module {@code cat} chưa expose named interface "api" có query port đọc hồ sơ mèo (chỉ có
 * event + DTO ở M0). Vì vậy quyền sở hữu mèo (`CAT_NOT_OWNED`) và snapshot hiển thị (tên/giống)
 * được đọc trực tiếp bằng JDBC read-only trên bảng {@code cat} trong
 * {@code scan.infrastructure.persistence} — KHÔNG import type nào của module {@code cat} cho việc
 * đó. Khai {@code "cat::api"} chỉ vì MỘT lý do riêng: tái dùng nguyên
 * {@link com.catcheck.cat.api.CatErrorCode#CAT_ARCHIVED} thay vì định nghĩa trùng tên trong
 * {@code ScanErrorCode} (xem javadoc lớp đó — {@code ErrorCode.code()} phải duy nhất toàn hệ
 * thống). Xem {@code docs/handovers/A6.md} mục judgment call.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "credit::api", "cat::api", "media::api", "audit::api", "privacy::spi" })
package com.catcheck.scan;
