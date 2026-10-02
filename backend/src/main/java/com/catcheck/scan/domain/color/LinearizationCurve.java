package com.catcheck.scan.domain.color;

/**
 * Đường cong tuyến tính hoá thang xám của camera — bước S4 (p6 §6.5.1 S4, kiểu {@code GRAY_POLY3}).
 *
 * <h2>Vấn đề mà lớp này giải quyết</h2>
 * <p>Camera không nội suy tuyến tính giữa 0 và 1. Nó áp một đường cong tone riêng (thường gamma
 * ~2.2 nhưng không bao giờ đúng 2.2 và không bao giờ giống nhau giữa các máy). Muốn có Lab đúng, phải
 * <b>đo</b> đường cong đó từ dải xám trên thẻ rồi khử ngược.
 *
 * <h2>Đa thức bậc 3, và điều kiện bắt buộc: đơn điệu</h2>
 * <p>Đa thức bậc 3 là lựa chọn nhỏ nhất vừa khớp dữ liệu. Nhưng đa thức bậc 3 <b>không bảo đảm
 * đơn điệu</b>: fit trên dải [0,1] có thể quay đầu ở giữa, sinh ra cùng một giá trị cho hai giá trị
 * đầu vào khác nhau. Hệ quả trong hệ thống này: <em>cùng một màu thật sẽ cho hai kết quả pH khác
 * nhau tùy lúc trong ảnh</em> — lỗi không tái hiện được, không chẩn đoán được, không nhìn thấy
 * được. Vì vậy {@link #isMonotonic()} là <b>điều kiện nghiệm thu</b>, không phải tối ưu hoá: không
 * đơn điệu thì rơi về sRGB EOTF chuẩn (p6 S4, cột "điều kiện thất bại").
 *
 * <p>Cùng lý do với thẻ: dải xám phải <b>đơn điệu theo L*</b>, không thì thẻ in sai.
 */
public record LinearizationCurve(Type type, GrayPoly3 polynomial) {

    /** Nguồn của đường cong. */
    public enum Type {
        /** Dùng đường cong đo được từ dải xám trên thẻ. */
        MEASURED,
        /** Rơi về sRGB EOTF chuẩn IEC 61966-2-1. */
        STANDARD_SRGB
    }

    /** Hệ số đa thức {@code linear = c0 + c1·s + c2·s² + c3·s³}. */
    public record GrayPoly3(double c0, double c1, double c2, double c3) {

        /** Giá trị tại điểm đầu vào. */
        public double evaluate(double s) {
            return c0 + c1 * s + c2 * s * s + c3 * s * s * s;
        }

        /** Đạo hàm — dùng để kiểm tra đơn điệu. */
        public double derivative(double s) {
            return c1 + 2 * c2 * s + 3 * c3 * s * s;
        }

        /** Mảng 4 hệ số để serialize vào {@code scan_analysis.linearization.coeffs}. */
        public double[] toArray() {
            return new double[] {c0, c1, c2, c3};
        }
    }

    /** Số bước lấy mẫu khi kiểm tra đơn điệu. */
    private static final int MONOTONIC_STEPS = 200;

    public LinearizationCurve {
        if (type == null) {
            throw new IllegalArgumentException("Loai duong cong khong duoc null");
        }
        if (type == Type.MEASURED && polynomial == null) {
            throw new IllegalArgumentException("Duong cong do duoc phai co da thuc bac 3");
        }
    }

    public static LinearizationCurve measured(GrayPoly3 polynomial) {
        return new LinearizationCurve(Type.MEASURED, polynomial);
    }

    /** sRGB EOTF chuẩn — phương án dự phòng khi fit thẻ không dùng được. */
    public static LinearizationCurve standard() {
        return new LinearizationCurve(Type.STANDARD_SRGB, null);
    }

    /** Khử gamma giá trị sRGB đã encode trong {@code [0,1]} → linear. */
    public double linearize(double encoded) {
        return type == Type.STANDARD_SRGB
                ? ColorSpace.srgbToLinear(encoded)
                : polynomial.evaluate(encoded);
    }

    /**
     * Kiểm tra đơn điệu tăng trên {@code [0,1]}.
     *
     * <p>Duyệt <b>đạo hàm</b> chứ không chỉ so hai đầu: đa thức bậc 3 có thể tăng ở hai đầu và lõm ở
     * giữa, mà so hai đầu thì vẫn "tăng" — bỏ sót đúng lỗi nguy hiểm nhất.
     */
    public boolean isMonotonic() {
        if (type == Type.STANDARD_SRGB) {
            return true;
        }
        for (int i = 0; i <= MONOTONIC_STEPS; i++) {
            double s = (double) i / MONOTONIC_STEPS;
            if (polynomial.derivative(s) < -1e-9) {
                return false;
            }
        }
        return true;
    }

    /**
     * Bước S4: nhận đường cong đo được, dùng nó nếu hợp lệ, không thì rơi về sRGB EOTF chuẩn.
     *
     * @param measured đa thức fit từ dải xám trên thẻ, có thể {@code null}
     * @return đường cong dùng thật, <b>không bao giờ null</b>
     */
    public static LinearizationCurve resolveOrStandard(GrayPoly3 measured) {
        if (measured == null) {
            return standard();
        }
        LinearizationCurve candidate = measured(measured);
        if (!candidate.isMonotonic()) {
            return standard();
        }
        for (int i = 0; i <= MONOTONIC_STEPS; i++) {
            double value = candidate.linearize((double) i / MONOTONIC_STEPS);
            if (value < -1e-6 || value > 1.0 + 1e-6) {
                return standard();
            }
        }
        return candidate;
    }

    /** Số ô xám tối thiểu để fit bậc 3 — ít hơn thì hệ phương trình bậc 4 bị khoá cột. */
    public static final int MIN_GRAY_PATCHES = 4;

    /**
     * Fit đa thức bậc 3 bằng bình phương nhỏ nhất trên dải xám của thẻ: {@code linear = f(encoded)}.
     *
     * <p>Máy ảnh tốt có thể cho dải xám trên thẻ chỉ 5–6 ô dùng được, nên bậc 3 (4 hệ số) là trần
     * hợp lý — bậc cao hơn bắt đầu khớp nhiễu in thay vì khớp tone. Cùng lý do: dải xám phải
     * <b>đơn điệu theo L*</b>, không thì thẻ in sai và mọi ô màu cũng sai theo.
     *
     * @param encoded           giá trị 8-bit đã encode của từng ô xám, chuẩn hoá về {@code [0,1]}
     * @param linearReferences  giá trị linear tham chiếu của từng ô (ví dụ từ Xyz rồi quy đổi)
     * @return đa thức đã fit
     * @throws IllegalArgumentException khi thiếu dữ liệu hoặc hệ không xác định
     */
    public static GrayPoly3 fit(double[] encoded, double[] linearReferences) {
        if (encoded == null || linearReferences == null || encoded.length != linearReferences.length) {
            throw new IllegalArgumentException("Can hai mang cung do dai");
        }
        if (encoded.length < MIN_GRAY_PATCHES) {
            throw new IllegalArgumentException("Can it nhat " + MIN_GRAY_PATCHES
                    + " o xam de fit bac 3, nhan duoc " + encoded.length);
        }
        for (double s : encoded) {
            if (s < -1e-9 || s > 1.0 + 1e-9) {
                throw new IllegalArgumentException("Gia tri xam phai trong [0,1], nhan duoc " + s);
            }
        }

        int order = 4;
        double[][] a = new double[order][order + 1];
        for (int k = 0; k < order; k++) {
            for (int j = 0; j < order; j++) {
                a[k][j] = 0.0;
            }
            a[k][order] = 0.0;
        }
        for (int i = 0; i < encoded.length; i++) {
            double[] basis = {1.0, encoded[i], encoded[i] * encoded[i], encoded[i] * encoded[i] * encoded[i]};
            for (int k = 0; k < order; k++) {
                for (int j = 0; j < order; j++) {
                    a[k][j] += basis[k] * basis[j];
                }
                a[k][order] += basis[k] * linearReferences[i];
            }
        }

        double[] coeffs = solve(a, order);
        return new GrayPoly3(coeffs[0], coeffs[1], coeffs[2], coeffs[3]);
    }

    /**
     * Fit dải xám rồi áp điều kiện nghiệm thu: không đơn điệu hoặc tràn {@code [0,1]} thì rơi về
     * sRGB EOTF chuẩn.
     *
     * @param encoded          giá trị 8-bit đã encode
     * @param linearReferences giá trị linear tham chiếu
     */
    public static LinearizationCurve fitOrStandard(double[] encoded, double[] linearReferences) {
        try {
            return resolveOrStandard(fit(encoded, linearReferences));
        } catch (IllegalArgumentException e) {
            return standard();
        }
    }

    /**
     * Khử Gauss có hoán vị một phần cho hệ vuông nhỏ {@code n×(n+1)} tăng dần.
     *
     * <p>Hệ 4×4 ổn định về số học; hoán vị một phần chỉ để tránh chia cho phần tử cực nhỏ khi các ô
     * xám tụ cục, trường hợp xảy ra khi thẻ bị chụp thiếu sáng.
     */
    private static double[] solve(double[][] augmented, int n) {
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(augmented[row][col]) > Math.abs(augmented[pivot][col])) {
                    pivot = row;
                }
            }
            if (Math.abs(augmented[pivot][col]) < 1e-12) {
                throw new IllegalArgumentException(
                        "He phuong trinh fit bi ky — duong cong khong xac dinh, hay la o xam trung lap");
            }
            double[] swap = augmented[col];
            augmented[col] = augmented[pivot];
            augmented[pivot] = swap;

            for (int row = 0; row < n; row++) {
                if (row == col) {
                    continue;
                }
                double factor = augmented[row][col] / augmented[col][col];
                if (factor == 0.0) {
                    continue;
                }
                for (int k = col; k <= n; k++) {
                    augmented[row][k] -= factor * augmented[col][k];
                }
            }
        }
        double[] solution = new double[n];
        for (int i = 0; i < n; i++) {
            solution[i] = augmented[i][n] / augmented[i][i];
        }
        return solution;
    }
}
