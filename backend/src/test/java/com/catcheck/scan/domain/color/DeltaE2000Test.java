package com.catcheck.scan.domain.color;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Đối chiếu CIEDE2000 với <b>toàn bộ 34 cặp tham chiếu</b> của Sharma, Wu &amp; Dalal (2005) —
 * "The CIEDE2000 Color-Difference Formula: Implementation Notes, Supplementary Test Data, and
 * Mathematical Observations", Color Research &amp; Application 30(1).
 *
 * <p>Sai số chấp nhận <b>&lt; 1e-4</b> (p6 §6.13.2 tầng 1 — ghi rõ "không thương lượng"). Lý do chọn
 * ngưỡng này: công thức có bẫy wrap-around hue quanh 0°/360° và thành phần {@code R_T}; bảng tham
 * chiếu chỉ chứa số làm tròn 4 chữ số thập phân, nên 1e-4 vừa đủ bắt lỗi thật mà không đòi hỏi
 * khớp tuyệt đối với bản in.
 *
 * <p>⚠ Bảng 34 cặp được sinh với {@code kL = kC = kH = 1}. Mọi phép khớp bảng màu của CatCheck lại
 * dùng {@code kL = 2} (p6 §6.5.1 (a)). Vì vậy test này <b>luôn</b> truyền
 * {@link DeltaE2000Params#STANDARD} — xem {@link #catCheckParamsHalveTheLightnessWeight()}.
 */
class DeltaE2000Test {

    // ------------------------------------------------------------------ 34 cặp chuẩn Sharma et al.

    @ParameterizedTest(name = "Sharma #{index}: ΔE00 mong doi {8}, that {9}")
    @CsvSource(textBlock = """
              50.0000,   2.6772, -79.7751,  50.0000,  0.0000, -82.7485,  2.0425
              50.0000,   3.1571, -77.2803,  50.0000,  0.0000, -82.7485,  2.8615
              50.0000,   2.8361, -74.0200,  50.0000,  0.0000, -82.7485,  3.4412
              50.0000,  -1.3802, -84.2814,  50.0000,  0.0000, -82.7485,  1.0000
              50.0000,  -1.1848, -84.8006,  50.0000,  0.0000, -82.7485,  1.0000
              50.0000,  -0.9009, -85.5211,  50.0000,  0.0000, -82.7485,  1.0000
              50.0000,   0.0000,   0.0000,  50.0000, -1.0000,   2.0000,  2.3669
              50.0000,  -1.0000,   2.0000,  50.0000,  0.0000,   0.0000,  2.3669
              50.0000,   2.4900,  -0.0010,  50.0000, -2.4900,   0.0009,  7.1792
              50.0000,   2.4900,  -0.0010,  50.0000, -2.4900,   0.0010,  7.1792
              50.0000,   2.4900,  -0.0010,  50.0000, -2.4900,   0.0011,  7.2195
              50.0000,   2.4900,  -0.0010,  50.0000, -2.4900,   0.0012,  7.2195
              50.0000,  -0.0010,   2.4900,  50.0000,  0.0009,  -2.4900,  4.8045
              50.0000,  -0.0010,   2.4900,  50.0000,  0.0010,  -2.4900,  4.8045
              50.0000,  -0.0010,   2.4900,  50.0000,  0.0011,  -2.4900,  4.7461
              50.0000,   2.5000,   0.0000,  50.0000,  0.0000,  -2.5000,  4.3065
              50.0000,   2.5000,   0.0000,  73.0000, 25.0000, -18.0000, 27.1492
              50.0000,   2.5000,   0.0000,  61.0000, -5.0000,  29.0000, 22.8977
              50.0000,   2.5000,   0.0000,  56.0000,-27.0000,  -3.0000, 31.9030
              50.0000,   2.5000,   0.0000,  58.0000, 24.0000,  15.0000, 19.4535
              50.0000,   2.5000,   0.0000,  50.0000,  3.1736,   0.5854,  1.0000
              50.0000,   2.5000,   0.0000,  50.0000,  3.2972,   0.0000,  1.0000
              50.0000,   2.5000,   0.0000,  50.0000,  1.8634,   0.5757,  1.0000
              50.0000,   2.5000,   0.0000,  50.0000,  3.2592,   0.3350,  1.0000
              60.2574, -34.0099,  36.2677,  60.4626,-34.1751,  39.4387,  1.2644
              63.0109, -31.0961,  -5.8663,  62.8187,-29.7946,  -4.0864,  1.2630
              61.2901,   3.7196,  -5.3901,  61.4292,  2.2480,  -4.9620,  1.8731
              35.0831, -44.1164,   3.7933,  35.0232,-40.0716,   1.5901,  1.8645
              22.7233,  20.0904, -46.6940,  23.0331, 14.9730, -42.5619,  2.0373
              36.4612,  47.8580,  18.3852,  36.2715, 50.5065,  21.2231,  1.4146
              90.8027,  -2.0831,   1.4410,  91.1528, -1.6435,   0.0447,  1.4441
              90.9257,  -0.5406,  -0.9208,  88.6381, -0.8985,  -0.7239,  1.5381
               6.7747,  -0.2908,  -2.4247,   5.8714, -0.0985,  -2.2286,  0.6377
               2.0776,   0.0795,  -1.1350,   0.9033, -0.0636,  -0.5514,  0.9082
            """)
    @DisplayName("34 cặp chuẩn Sharma et al. — sai số < 1e-4")
    void sharmaReferencePairs(double l1, double a1, double b1,
                              double l2, double a2, double b2, double expected) {
        double actual = DeltaE2000.deltaE(new Lab(l1, a1, b1), new Lab(l2, a2, b2),
                DeltaE2000Params.STANDARD);

        assertThat(actual)
                .as("CIEDE2000 cho cap (%s,%s,%s)-(%s,%s,%s)", l1, a1, b1, l2, a2, b2)
                .isCloseTo(expected, within(1e-4));
    }

    // ------------------------------------------------------------------ bẫy đã biết

    @Test
    @DisplayName("Bẫy 1 — màu trung tính: C' = 0 thì ΔH' phải bằng 0, không được NaN")
    void neutralColorsGiveZeroHueDifference() {
        double de = DeltaE2000.deltaE(new Lab(50.0, 0.0, 0.0), new Lab(50.0, 0.0, 0.0),
                DeltaE2000Params.STANDARD);

        assertThat(de).isCloseTo(0.0, within(1e-12));
        // Cặp đối xứng trong bảng chuẩn: màu trung tính ↔ màu có a*, b*.
        assertThat(DeltaE2000.deltaE(new Lab(50.0, 0.0, 0.0), new Lab(50.0, -1.0, 2.0),
                DeltaE2000Params.STANDARD)).isCloseTo(2.3669, within(1e-4));
    }

    @Test
    @DisplayName("Bẫy 2 — wrap-around hue quanh 0/360 độ: 2.4900/-0.0010 phải khớp 7.1792")
    void hueWrapAroundIsHandled() {
        // Trục đỏ (hue ≈ 0°) nằm ngay ranh giới atan2; nếu quên cộng 360° thì kết quả lệch hàng chục.
        double de = DeltaE2000.deltaE(new Lab(50.0, 2.49, -0.001), new Lab(50.0, -2.49, 0.0009),
                DeltaE2000Params.STANDARD);

        assertThat(de).isCloseTo(7.1792, within(1e-4));
    }

    @Test
    @DisplayName("Tính đối xứng — ΔE00(a,b) == ΔE00(b,a) trên các cặp khó nhất")
    void distanceIsSymmetric() {
        double[][][] pairs = {
                {{50.0, 2.6772, -79.7751}, {50.0, 0.0, -82.7485}},
                {{50.0, -1.3802, -84.2814}, {50.0, 0.0, -82.7485}},
                {{50.0, 2.49, -0.001}, {50.0, -2.49, 0.0009}},
                {{50.0, 2.5, 0.0}, {73.0, 25.0, -18.0}},
                {{50.0, 2.5, 0.0}, {56.0, -27.0, -3.0}},
                {{60.2574, -34.0099, 36.2677}, {60.4626, -34.1751, 39.4387}},
                {{2.0776, 0.0795, -1.135}, {0.9033, -0.0636, -0.5514}},
        };
        for (double[][] pair : pairs) {
            Lab x = Lab.of(pair[0]);
            Lab y = Lab.of(pair[1]);
            assertThat(DeltaE2000.deltaE(x, y, DeltaE2000Params.STANDARD))
                    .as("doi xung cho %s vs %s", x, y)
                    .isCloseTo(DeltaE2000.deltaE(y, x, DeltaE2000Params.STANDARD), within(1e-12));
        }
    }

    @Test
    @DisplayName("Giống hệt nhau thì ΔE00 = 0")
    void identicalColorsHaveZeroDistance() {
        Lab lab = new Lab(63.01, -31.0961, -5.8663);
        assertThat(DeltaE2000.deltaE(lab, lab, DeltaE2000Params.CAT_CHECK)).isCloseTo(0.0, within(1e-12));
    }

    // ------------------------------------------------------------------ tham số sản phẩm

    @Test
    @DisplayName("kL = 2 giảm ảnh hưởng của L*: ΔE00 nhỏ hơn, và phải = 5.0 khi chỉ khác L*")
    void catCheckParamsHalveTheLightnessWeight() {
        Lab darker = new Lab(45.0, 10.0, -20.0);
        Lab lighter = new Lab(55.0, 10.0, -20.0);

        double standard = DeltaE2000.deltaE(darker, lighter, DeltaE2000Params.STANDARD);
        double catCheck = DeltaE2000.deltaE(darker, lighter, DeltaE2000Params.CAT_CHECK);

        assertThat(catCheck)
                .as("kL=2 phai cho ΔE00 nho hon ban chuan")
                .isLessThan(standard);
        // Với ΔL' = 10, sL = 1 tại L̄' = 50: chuẩn cho 10.0000, kL=2 cho 5.0000.
        assertThat(catCheck).isCloseTo(5.0, within(1e-9));
    }

    @Test
    @DisplayName("Tham số sai (kL <= 0) bị từ chối ngay lúc khởi tạo")
    void invalidParamsAreRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DeltaE2000Params(0.0, 1.0, 1.0));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DeltaE2000Params(1.0, -1.0, 1.0));
    }
}
