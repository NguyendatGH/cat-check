package com.catcheck.credit.domain.port;

/**
 * Cổng băm mã kích hoạt. Thuật toán thật đặt ở {@code ..infrastructure.crypto..} vì nó gắn với
 * biến môi trường; domain chỉ cần biết "băm thành chuỗi hex 64 ký tự kèm version pepper".
 *
 * <p>Vì sao KHÔNG phải SHA-256 trần (p11 §11.7.4): không gian mã chỉ ~3,5 × 10¹³ tổ hợp hợp
 * lệ checksum. SHA-256 không salt/pepper trên không gian đó là <b>duyệt được</b> — một GPU
 * hiện đại chạy cỡ 10¹⁰ hash/giây thì hết toàn bộ không gian trong khoảng <b>một giờ</b>.
 * Kẻ tấn công có bản dump DB sẽ khôi phục được toàn bộ mã chưa đổi và đổi chúng trước khách
 * hàng — mà mỗi mã là credit thật đã in trên bao bì. HMAC với pepper ≥ 32 byte lưu NGOÀI DB
 * làm bản dump DB một mình trở nên vô dụng.</p>
 */
public interface ActivationCodeHasher {

    /**
     * Băm một mã ĐÃ CHUẨN HOÁ ({@code ActivationCodeFormat.normalize}) bằng pepper hiện hành.
     *
     * @param normalizedCode mã đã chuẩn hoá, không có khoảng trắng, đã viết hoa
     * @return chuỗi hex 64 ký tự + version pepper đã dùng
     */
    HashedActivationCode hash(String normalizedCode);

    /**
     * Kết quả băm.
     *
     * @param hex           HMAC-SHA256 dạng hex lowercase, đúng 64 ký tự
     * @param pepperVersion version pepper đã dùng — lưu vào {@code activation_code.pepper_version}
     *                      để sau này verify được mã đã in ra bao bì
     */
    record HashedActivationCode(String hex, short pepperVersion) {

        public HashedActivationCode {
            if (hex == null || hex.length() != 64) {
                throw new IllegalArgumentException("hash phải đúng 64 ký tự hex");
            }
            if (pepperVersion < 1) {
                throw new IllegalArgumentException("pepperVersion phải >= 1");
            }
        }
    }
}
