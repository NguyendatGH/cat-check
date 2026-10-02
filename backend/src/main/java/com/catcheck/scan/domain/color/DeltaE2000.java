package com.catcheck.scan.domain.color;

/**
 * CIEDE2000 — công thức khoảng cách màu chuẩn của CIE (2005), tham chiếu bảng 34 cặp kiểm thử của
 * <b>Sharma, Wu &amp; Dalal (2005)</b>.
 *
 * <h2>Vì sao đây là hàm dễ sai nhất trong toàn hệ thống</h2>
 * <p>Công thức có <b>hai bẫy</b> đã làm hỏng nhiều hiện thực thương mại:
 * <ol>
 *   <li><b>Wrap-around quanh 0°/360°</b> ở góc hue. Khi một màu nằm ở 359° và màu kia ở 1° thì
 *       hiệu góc thô là −358° mà phải là +2°. Sai ở đây cho khoảng cách sai hàng chục đơn vị.</li>
 *   <li><b>Toàn bộ giá trị phải bình phương và nhân 25</b> ({@code C̄′ = (C₁ + C₂)/2},
 *       {@code G = ½·(1 − √(C̄′⁷ / (C̄′⁷ + 25⁷)))}). Bỏ nhân 25 làm hệ số hiệu chỉnh G sai hoàn toàn
 *       ở vùng chroma thấp — tức đúng vùng màu trung tính mà thẻ tham chiếu dùng để hiệu chuẩn.</li>
 * </ol>
 * <p>Hai bẫy này <b>không bao giờ</b> làm code crash — chúng chỉ làm kết quả sai lặng lẽ. Vì vậy
 * {@link DeltaE2000Test} đối chiếu đủ 34 cặp chuẩn với sai số &lt; 1e-4, và phép khớp bảng màu
 * (S8) chỉ dùng hàm này, không bao giờ dùng khoảng cách Euclid thay thế.
 *
 * <h2>Ghi chú tham số</h2>
 * <p>Mặc định của lớp này là {@link DeltaE2000Params#CAT_CHECK} ({@code kL = 2}) vì đó là tham số
 * của sản phẩm (p6 §6.5.1 S8). Muốn đối chiếu bảng Sharma phải truyền
 * {@link DeltaE2000Params#STANDARD}.
 */
public final class DeltaE2000 {

    /** Hằng số của công thức: {@code 25⁷} trong thành phần hiệu chỉnh G. */
    private static final double POW_25_7 = 6103515625.0;

    private DeltaE2000() {
        throw new AssertionError("DeltaE2000 la lop tien ich, khong instantiate");
    }

    /** ΔE00 với bộ tham số của sản phẩm ({@code kL = 2, kC = 1, kH = 1}). */
    public static double deltaE(Lab a, Lab b) {
        return deltaE(a, b, DeltaE2000Params.CAT_CHECK);
    }

    /**
     * CIEDE2000 đầy đủ.
     *
     * <p>Thứ tự triển khai bám sát văn bản chuẩn, đặc biệt chỗ tính {@code C̄′} <b>trước</b> khi dùng
     * vào G, và dùng {@code cos} của <b>góc trung bình có trọng số theo a,b,C</b> chứ không phải góc
     * trung bình cộng thẳng.
     *
     * @param a      màu thứ nhất
     * @param b      màu thứ hai
     * @param params hệ số kL/kC/kH
     * @return khoảng cách ΔE00, luôn {@code >= 0}
     */
    public static double deltaE(Lab a, Lab b, DeltaE2000Params params) {
        double l1 = a.l();
        double l2 = b.l();
        double a1 = a.a();
        double a2 = b.a();
        double b1 = a.b();
        double b2 = b.b();

        // --- 1. khoảng cách trong từng thành phần
        double c1Star = Math.hypot(a1, b1);
        double c2Star = Math.hypot(a2, b2);
        double cBarStar = (c1Star + c2Star) / 2.0;

        // --- 2. G: hiệu chỉnh bù cho vùng chroma thấp
        double cBarStar7 = Math.pow(cBarStar, 7.0);
        double g = 0.5 * (1.0 - Math.sqrt(cBarStar7 / (cBarStar7 + POW_25_7)));

        // --- 3. a′ (đã hiệu chỉnh) và C′
        double a1Prime = (1.0 + g) * a1;
        double a2Prime = (1.0 + g) * a2;
        double c1Prime = Math.hypot(a1Prime, b1);
        double c2Prime = Math.hypot(a2Prime, b2);

        // --- 4. hue: đo bằng atan2 để không mất nhánh ở vùng a* ≈ 0, b* < 0
        double h1Prime = hueAngle(b1, a1Prime);
        double h2Prime = hueAngle(b2, a2Prime);

        // --- 5. ΔL′, ΔC′, ΔH′
        double deltaLPrime = l2 - l1;
        double deltaCPrime = c2Prime - c1Prime;

        double cBarPrime = (c1Prime + c2Prime) / 2.0;
        double hBarPrime = hueMean(h1Prime, h2Prime, c1Prime, c2Prime);

        // --- 6. ΔH′. Công thức gốc dùng sin((h₂′ − h₁′)/2) — biểu thức này sai dấu khi chênh lấn
        //        360° và cho NaN khi một màu trung tính, nên phải đi qua nhánh ΔH′ lớn đã chuẩn hoá
        //        về (−180°, 180°] rồi mới lấy sin nửa góc.
        double deltaBigHuePrime;
        if (c1Prime * c2Prime == 0.0) {
            deltaBigHuePrime = 0.0;
        } else if (Math.abs(h2Prime - h1Prime) <= 180.0) {
            deltaBigHuePrime = h2Prime - h1Prime;
        } else if (h2Prime - h1Prime > 180.0) {
            deltaBigHuePrime = h2Prime - h1Prime - 360.0;
        } else {
            deltaBigHuePrime = h2Prime - h1Prime + 360.0;
        }
        double deltaHPrime = 2.0 * Math.sqrt(c1Prime * c2Prime)
                * Math.sin(toRadians(deltaBigHuePrime) / 2.0);

        // --- 7. trung bình L̄′
        double lBarPrime = (l1 + l2) / 2.0;

        // --- 8. T: hiệu chỉnh cho vùng sáng trung bình và vùng hue vàng (CIE thêm vì trực giác
        //        "vùng xanh lam khó phân biệt hơn" và "gradient sáng tối quanh L̄′ = 50 khó nhìn").
        double t = 1.0
                - 0.17 * Math.cos(toRadians(hBarPrime - 30.0))
                + 0.24 * Math.cos(toRadians(2.0 * hBarPrime))
                + 0.32 * Math.cos(toRadians(3.0 * hBarPrime + 6.0))
                - 0.20 * Math.cos(toRadians(4.0 * hBarPrime - 63.0));

        double deltaTheta = 30.0 * Math.exp(-Math.pow((hBarPrime - 275.0) / 25.0, 2.0));
        double cBarPrime7 = Math.pow(cBarPrime, 7.0);
        double rC = 2.0 * Math.sqrt(cBarPrime7 / (cBarPrime7 + POW_25_7));
        double lBarPrimeMinus50Squared = (lBarPrime - 50.0) * (lBarPrime - 50.0);
        double sC = 1.0 + 0.045 * cBarPrime;
        double sL = 1.0 + 0.015 * lBarPrimeMinus50Squared / Math.sqrt(20.0 + lBarPrimeMinus50Squared);
        double sH = 1.0 + 0.015 * cBarPrime * t;
        double rT = -Math.sin(toRadians(2.0 * deltaTheta)) * rC;

        // --- 9. gộp
        double termL = deltaLPrime / (params.kL() * sL);
        double termC = deltaCPrime / (params.kC() * sC);
        double termH = deltaHPrime / (params.kH() * sH);
        double squared = termL * termL + termC * termC + termH * termH + rT * termC * termH;
        return squared <= 0.0 ? 0.0 : Math.sqrt(squared);
    }

    /**
     * ΔE00 giữa hai mảng {@code [L, a, b]} — dùng khi đọc thẳng {@code lab_l/a/b} từ
     * {@code scan_analysis} cho backfill (p6 §6.5.4).
     */
    public static double deltaE(double[] lab1, double[] lab2, DeltaE2000Params params) {
        return deltaE(Lab.of(lab1), Lab.of(lab2), params);
    }

    /**
     * Khoảng cách ΔE00 <b>tương đối</b> giữa một mẫu đo và một ô màu trên thẻ tham chiếu trong
     * <em>cùng ảnh đó</em> — chế độ {@code ON_CARD_RELATIVE} (research S8, p6 §6.5.1 (c)).
     *
     * <p>Cùng hàm với {@link #deltaE}, nhưng tồn tại thành method riêng để nơi gọi nói rõ ý nghĩa:
     * mẫu và tham chiếu chịu cùng một biến dạng illuminant/camera/tone-mapping, nên phần lớn sai
     * số bị triệt tiêu. Bảng Lab trong DB chỉ là fallback + cross-check.
     */
    public static double deltaERelative(Lab sample, Lab cardPatch, DeltaE2000Params params) {
        return deltaE(sample, cardPatch, params);
    }

    /** Góc hue trong {@code [0, 360)}; trả {@code 0} khi màu là trung tính. */
    private static double hueAngle(double bStar, double aPrime) {
        if (aPrime == 0.0 && bStar == 0.0) {
            return 0.0;
        }
        double degrees = Math.toDegrees(Math.atan2(bStar, aPrime));
        return degrees < 0.0 ? degrees + 360.0 : degrees;
    }

    /** Hue trung bình theo đúng quy tắc của CIE (xử lý cả trường hợp bọc qua 0°/360°). */
    private static double hueMean(double h1, double h2, double c1, double c2) {
        if (c1 * c2 == 0.0) {
            return h1 + h2;
        }
        double diff = Math.abs(h1 - h2);
        if (diff <= 180.0) {
            return (h1 + h2) / 2.0;
        }
        if (h1 + h2 < 360.0) {
            return (h1 + h2 + 360.0) / 2.0;
        }
        return (h1 + h2 - 360.0) / 2.0;
    }

    private static double toRadians(double degrees) {
        return degrees * Math.PI / 180.0;
    }
}
