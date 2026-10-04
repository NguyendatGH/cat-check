package com.catcheck.credit.domain;

/**
 * Nội dung một lần sửa {@code package_plan} — L26 {@code PATCH /admin/package-plans/{code}}.
 *
 * <p>Mọi field {@code null} = KHÔNG đổi (ngữ nghĩa PATCH của p8 §8.1.6). Vì vậy không dùng kiểu
 * nguyên thuỷ: {@code int creditAmount = 0} không phân biệt được "không gửi" với "gửi 0".</p>
 *
 * <p>Không có {@code code} và không có {@code weightKg}: {@code code} là khoá chính (p4 §4.1.1
 * — đổi khoá chính của một bảng bị {@code activation_code}/{@code credit_batch} tham chiếu là
 * một migration chứ không phải một lần PATCH), còn {@code weight_kg} là đặc tính vật lý của bao
 * sản phẩm, đổi nó nghĩa là một gói khác. p8 L26 cũng chỉ liệt kê bốn trường dưới đây.</p>
 *
 * @param creditAmount       số credit mỗi lần kích hoạt
 * @param creditValidityDays hạn credit tính từ {@code activated_at}
 * @param maxCatProfiles     số hồ sơ mèo tối đa; {@code null} = không đổi (xem {@code clearMaxCatProfiles})
 * @param features           cờ tính năng, ghi đè cả khối
 * @param active             còn bán hay không — p14 §14.3.2 mục 5: KHÔNG cho xoá gói, chỉ tắt
 * @param clearMaxCatProfiles đặt {@code max_cat_profiles} về {@code NULL} ("không giới hạn").
 *                           Cần cờ riêng vì {@code null} ở trường trên đã mang nghĩa "không đổi"
 */
public record PackagePlanUpdate(
        Integer creditAmount,
        Integer creditValidityDays,
        Integer maxCatProfiles,
        PlanFeatures features,
        Boolean active,
        boolean clearMaxCatProfiles
) {

    public PackagePlanUpdate {
        if (creditAmount != null && creditAmount <= 0) {
            throw new IllegalArgumentException("creditAmount phải > 0");
        }
        if (creditValidityDays != null && creditValidityDays <= 0) {
            throw new IllegalArgumentException("creditValidityDays phải > 0");
        }
        if (maxCatProfiles != null && maxCatProfiles <= 0) {
            throw new IllegalArgumentException("maxCatProfiles phải > 0 hoặc null (không giới hạn)");
        }
        if (clearMaxCatProfiles && maxCatProfiles != null) {
            throw new IllegalArgumentException(
                    "không thể vừa đặt maxCatProfiles vừa xoá về không giới hạn");
        }
    }

    /** Có gì để ghi không — PATCH rỗng không được tăng {@code version} một cách vô nghĩa. */
    public boolean isEmpty() {
        return creditAmount == null && creditValidityDays == null && maxCatProfiles == null
                && features == null && active == null && !clearMaxCatProfiles;
    }
}
