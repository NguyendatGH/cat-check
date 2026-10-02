package com.catcheck.credit.infrastructure.crypto;

import com.catcheck.credit.domain.port.ActivationCodeHasher;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * {@link ActivationCodeHasher} dùng <b>HMAC-SHA256</b> với pepper lấy từ biến môi trường
 * (p11 §11.7.4).
 *
 * <p>Vì sao không phải SHA-256 trần: không gian mã chỉ ~3,5 × 10¹³ tổ hợp hợp lệ (9 ký tự
 * ngẫu nhiên Crockford Base32 × ký tự checksum). Một GPU hiện đại dò khoảng 10¹⁰ hash/s nên quét
 * hết số mã trong chưa tới một giờ. Kẻ tấn công chỉ cần một bản dump DB là thu được toàn bộ mã
 * chưa quy đổi — mà mỗi mã là credit thật đã in trên bao bì. HMAC với pepper nằm NGOÀI DB làm
 * bản dump đó vô dụng.</p>
 *
 * <p>Pepper đọc từ biến môi trường, không bao giờ từ bảng cấu hình: mọi bản dump DB — kể cả
 * dump của chính ứng dụng — đều không chứa pepper (p18: bí mật thật thuộc biến môi trường).</p>
 *
 * <p>Không có cache, không lưu pepper vào log. Đối chiếu chỉ bằng cách băm lại rồi so chuỗi
 * hex, không dùng {@code MessageDigest.isEqual} vì ở đây không có tấn công timing qua đường
 * so sánh (giá trị tra về là duy nhất từ khoá UNIQUE).</p>
 */
@Component
public class HmacActivationCodeHasher implements ActivationCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final HexFormat HEX = HexFormat.of();

    /** Chuỗi pepper không dài hơn thế này là không đạt: 32 byte là mốc tối thiểu của HMAC-SHA256. */
    private static final int MIN_PEPPER_BYTES = 32;

    private final byte[] pepperKey;
    private final short pepperVersion;

    public HmacActivationCodeHasher(ActivationPepperProperties properties) {
        byte[] pepper = properties.pepper().getBytes(StandardCharsets.UTF_8);
        if (pepper.length < MIN_PEPPER_BYTES) {
            // Fail-fast lúc khởi động chứ không phải lúc có người nhập mã: một pepper quá ngắn
            // vẫn chạy được nhưng lại yếu, và không ai nhớ kiểm tra cho tới khi có sự cố.
            throw new IllegalStateException(
                    "ACTIVATION_PEPPER phải dài tối thiểu " + MIN_PEPPER_BYTES + " byte, nhận: " + pepper.length);
        }
        this.pepperKey = pepper;
        this.pepperVersion = properties.pepperVersion();
    }

    @Override
    public HashedActivationCode hash(String normalizedCode) {
        if (normalizedCode == null || normalizedCode.isEmpty()) {
            throw new IllegalArgumentException("Mã rỗng không thể băm");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepperKey, ALGORITHM));
            return new HashedActivationCode(HEX.formatHex(mac.doFinal(
                    normalizedCode.getBytes(StandardCharsets.UTF_8))), pepperVersion);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không khởi tạo được " + ALGORITHM, ex);
        }
    }

}
