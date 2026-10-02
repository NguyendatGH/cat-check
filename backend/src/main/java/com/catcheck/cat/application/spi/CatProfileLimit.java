package com.catcheck.cat.application.spi;

/**
 * Hạn mức mèo theo gói, đủ dữ liệu để dựng câu CTA đúng cho người dùng.
 *
 * <p>{@code requiredPackageCode} tồn tại chỉ vì lý do copy: p8 §8.2.4 yêu cầu thông báo
 * {@code CAT_PROFILE_LIMIT_REACHED} nói rõ "Kích hoạt gói {requiredPackage} để thêm bé mới". Nếu chỉ
 * trả về {@code max} thì không có gì để điền vào chỗ đó, và câu chữ sẽ trở thành mẹo.</p>
 *
 * @param max                 số mèo tối đa gói cho phép
 * @param requiredPackageCode mã gói cần kích hoạt để nâng hạn mức; {@code null} nếu không có gói nào
 *                            nâng được (khi đó câu CTA hướng về hỗ trợ thay vì nâng gói)
 */
public record CatProfileLimit(int max, String requiredPackageCode) {
}
