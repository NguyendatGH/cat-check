package com.catcheck.credit.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một mã kích hoạt đã phát hành, chỉ đọc — dòng {@code activation_code}.
 *
 * <p><b>KHÔNG có trường nào giữ mã thô.</b> Mã thô chỉ tồn tại trong giá trị trả về của
 * {@code issueBatch} đúng một lần, rồi được ghi ra CSV cho khâu in bao bì (p5 §5.9). Sau đó
 * trong DB chỉ còn {@link #codeHash} — nên xoay pepper mà không giữ được version cũ để verify
 * sẽ làm mọi mã chưa đổi vô hiệu vĩnh viễn (p11 §11.7.4, OQ-5).</p>
 *
 * @param id               UUID v7
 * @param codeHash         {@code HMAC-SHA256(pepper, code)} dạng hex 64 ký tự
 * @param pepperVersion    version pepper đã dùng để băm — bắt buộc để verify được mã cũ
 * @param codePrefix       tiền tố để hỗ trợ tra cứu, ví dụ {@code "PLUS-"}
 * @param packageCode      mã gói mà mã này đổi được
 * @param productionBatch  lô sản xuất, liên kết bảng màu pH
 * @param issuedAt         thời điểm phát hành
 * @param validUntil       hạn KÍCH HOẠT (khác hạn credit)
 * @param status           trạng thái hiện tại
 * @param redeemedBy       tài khoản đã đổi, null nếu chưa
 * @param redeemedAt       thời điểm đổi, null nếu chưa
 */
public record ActivationCode(
        UUID id,
        String codeHash,
        short pepperVersion,
        String codePrefix,
        String packageCode,
        String productionBatch,
        Instant issuedAt,
        Instant validUntil,
        ActivationCodeStatus status,
        UUID redeemedBy,
        Instant redeemedAt
) {

    public ActivationCode {
        if (id == null || codeHash == null || packageCode == null || issuedAt == null || status == null) {
            throw new IllegalArgumentException("activationCode thiếu trường bắt buộc");
        }
        if (codeHash.length() != 64) {
            throw new IllegalArgumentException("activationCode.codeHash phải đúng 64 ký tự hex");
        }
        if (pepperVersion < 1) {
            throw new IllegalArgumentException("activationCode.pepperVersion phải >= 1");
        }
    }

    /** Mã đã hết hạn kích hoạt chưa (p5 §5.5: {@code valid_until} khác hạn credit). */
    public boolean isIssuanceExpiredAt(Instant now) {
        return validUntil != null && !validUntil.isAfter(now);
    }
}
