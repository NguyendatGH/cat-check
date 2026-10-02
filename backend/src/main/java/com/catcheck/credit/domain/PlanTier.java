package com.catcheck.credit.domain;

/**
 * Thứ tự ưu tiên của các gói — dùng để suy ra "gói cao nhất" cho
 * {@code user_entitlement.highest_package} (p5 R5: entitlement = hợp nhất MỨC CAO NHẤT trong
 * các gói user TỪNG kích hoạt).
 *
 * <p><b> Khoảng trống cần owner chốt — đã ghi trong {@code docs/handovers/A4.md}.</b>
 * {@code package_plan} (p5 §5.5, p4 §4.1.1) <b>không có cột thứ hạng</b>, trong khi p5 §5.2 lại
 * định nghĩa entitlement là "gói cao nhất". Schema vì vậy không biểu đạt được thứ tự đó.
 * Bảng 5 gói ở p5 §5.3 là thứ tự tăng dần về mức tính năng (MINI → DAILY → PLUS → MULTI →
 * CARE_BOX) nên ở MVP thứ tự đó được đặt ở đây, đúng một chỗ, để khi nào p4 thêm cột thứ hạng
 * thì chỉ cần thay enum này.</p>
 *
 * <p>Mã gói không nằm trong enum này (admin tự thêm gói mới vào {@code package_plan}) được xếp
 * CAO NHẤT — ngược lại thì một gói mới thêm vào sẽ không mở được quyền của gói cũ. Việc đó là
 * thất bại nghiêm trọng hơn việc một gói mới giành quyền của gói cũ, và admin thêm gói mới vẫn
 * phải duyệt qua UI quản trị có audit (p14).</p>
 */
public enum PlanTier {

    /** 1 kg — 3 credit, 1 hồ sơ mèo, không lịch sử/trend/reminder/export, không lưu ảnh. */
    MINI(1),

    /** 2,5 kg — 8 credit, 1 hồ sơ mèo, lịch sử cơ bản, có lưu ảnh. */
    DAILY(2),

    /** 3 kg — 10 credit, 1 hồ sơ mèo (neo duy nhất do owner chốt, p5 §5.3). */
    PLUS(3),

    /** 2 × 2,5 kg — 16 credit, không giới hạn hồ sơ mèo. */
    MULTI(4),

    /** 2 × 2,5 kg + phụ kiện — 16 credit, không giới hạn hồ sơ mèo. */
    CARE_BOX(5);

    private final int rank;

    PlanTier(int rank) {
        this.rank = rank;
    }

    /** Mã gói trong {@code package_plan.code}. */
    public String code() {
        return name();
    }

    public int rank() {
        return rank;
    }

    /**
     * Thứ hạng của một mã gói bất kỳ. Mã không nhận ra (gói admin thêm sau này) đứng trên
     * mọi gói đã biết — xem giải thích ở javadoc của enum.
     */
    public static int rankOf(String packageCode) {
        for (PlanTier tier : values()) {
            if (tier.code().equals(packageCode)) {
                return tier.rank;
            }
        }
        return Integer.MAX_VALUE;
    }

    /** {@code true} nếu {@code candidate} là gói cao hơn {@code current}. */
    public static boolean isHigher(String candidate, String current) {
        if (current == null) {
            return true;
        }
        return rankOf(candidate) > rankOf(current);
    }
}
