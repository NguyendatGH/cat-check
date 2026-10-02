/**
 * Kernel dùng chung cho mọi module (error, i18n, security, time, id, config). Không phụ thuộc
 * module nghiệp vụ nào — {@code allowedDependencies = {}} khai báo tường minh điều đó để
 * ArchUnit/Modulith bắt lỗi ngay nếu sau này ai đó lỡ import ngược từ shared vào một module
 * nghiệp vụ. Mọi module khác phụ thuộc vào nó bằng tên module trần
 * (allowedDependencies = {"shared"}), theo đúng bảng ở mục 2 của nhiệm vụ M0.
 *
 * <p>{@code type = ApplicationModule.Type.OPEN}: gần như mọi thứ hữu dụng của kernel này nằm ở
 * SUBPACKAGE ({@code shared.error}, {@code shared.id}, {@code shared.security}...), không phải ở
 * gói gốc. Một entry {@code allowedDependencies = {"shared"}} trần (không có {@code ::tên}) chỉ
 * cấp quyền vào named-interface "unnamed" — tức chỉ các type nằm TRỰC TIẾP ở gói gốc
 * {@code com.catcheck.shared}, KHÔNG lan sang subpackage dù gói gốc có {@code @NamedInterface}
 * riêng (đã xác nhận trực tiếp qua bytecode {@code spring-modulith-core}, xem
 * {@code docs/handovers/A1-backend-fix.md}). Không đánh dấu {@code OPEN} thì mọi module dùng
 * {@code shared.error.ErrorCode}/{@code shared.id.UuidV7}/{@code shared.security.*}... (tức là
 * HẦU HẾT module nghiệp vụ) đều bị {@code ModularityTests} báo vi phạm. {@code OPEN} là đúng ngữ
 * nghĩa cho một kernel dùng chung, không phải hạn chế cần dỡ dần — annotation
 * {@code @NamedInterface("shared")} ở dưới vẫn giữ để không phá tên named-interface "shared" mà
 * bảng phụ thuộc M0 đã dùng.</p>
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {},
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
@org.springframework.modulith.NamedInterface("shared")
package com.catcheck.shared;
