package com.catcheck.credit.domain;

/**
 * Bộ lọc của L19 {@code GET /admin/activation-codes?prefix=&packageCode=&status=&batchId=}.
 *
 * <p>p8 §8.4.12 ghi rõ <i>"không tra theo mã đầy đủ — chỉ có hash trong DB"</i>: không có
 * trường nào ở đây nhận mã thô, và cũng không thể có. Tra cứu một mã cụ thể phải đi đường băm
 * {@code HMAC-SHA256(pepper, code)} rồi so {@code code_hash} (p14 §14.3.2 mục 4), là một hành
 * động khác hẳn về mặt audit nên không gộp vào bộ lọc danh sách này.</p>
 *
 * <p>Mọi trường {@code null} = không lọc theo tiêu chí đó.</p>
 *
 * @param codePrefix      khớp đầu chuỗi {@code code_prefix}
 * @param packageCode     khớp đúng {@code package_code}
 * @param status          khớp đúng {@code status}
 * @param productionBatch khớp đúng {@code production_batch} (tham số {@code batchId} của p8)
 */
public record ActivationCodeFilter(
        String codePrefix,
        String packageCode,
        ActivationCodeStatus status,
        String productionBatch
) {

    public static ActivationCodeFilter none() {
        return new ActivationCodeFilter(null, null, null, null);
    }

    public ActivationCodeFilter {
        codePrefix = blankToNull(codePrefix);
        packageCode = blankToNull(packageCode);
        productionBatch = blankToNull(productionBatch);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
