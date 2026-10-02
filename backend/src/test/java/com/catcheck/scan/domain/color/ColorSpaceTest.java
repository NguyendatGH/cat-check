package com.catcheck.scan.domain.color;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm thử tầng chuyển đổi màu: sRGB ↔ linear ↔ XYZ ↔ CIELAB (D65/2°).
 *
 * <p>Đây là tầng mà <b>mọi con số pH đều dựa vào</b>. Ba loại lỗi được săn ở đây:
 * <ol>
 *   <li>Sai hằng số hoặc sai ma trận ⇒ màu lệch có hệ thống, nhưng đường khứ hồi vẫn "tròn xinh"
 *       nên test round-trip <b>không</b> bắt được. Vì vậy phải có anchor tuyệt đối.</li>
 *   <li>Sai <i>whitepoint</i> (D65 vs D50) — chỉ lộ ra ở màu bão hoà, không lộ ở màu xám.</li>
 *   <li>Dùng chuẩn <i>observer 10°</i> thay 2° — hệ số khác nhau ở bậc ba.</li>
 * </ol>
 * Nên bộ test này cố tình có cả anchor (giá trị tuyệt đối) lẫn round-trip (tính nhất quán).
 */
class ColorSpaceTest {

    // ------------------------------------------------------------------ round-trip

    @ParameterizedTest(name = "#{index}: round-trip qua Lab rồi về linear, sai số < 1e-6")
    @ValueSource(doubles = {0.0, 0.002, 0.01, 0.05, 0.1, 0.25, 0.5, 0.75, 0.9, 0.999, 1.0})
    void linearRoundTripThroughLabIsLossless(double value) {
        RgbLinear original = new RgbLinear(value, 1.0 - value, 0.5);
        RgbLinear restored = ColorSpace.labToLinearRgb(ColorSpace.linearRgbToLab(original));

        assertThat(restored.r()).isCloseTo(original.r(), within(1e-6));
        assertThat(restored.g()).isCloseTo(original.g(), within(1e-6));
        assertThat(restored.b()).isCloseTo(original.b(), within(1e-6));
    }

    @Test
    @DisplayName("sRGB 8-bit → Lab → sRGB 8-bit: khứ hồi tuyệt đối trên toàn bộ thang 0–255")
    void srgb8RoundTripIsExact() {
        for (int value = 0; value <= 255; value++) {
            Lab lab = ColorSpace.srgb8ToLab(value, value, value);
            ColorSpace.Srgb8 back = ColorSpace.hexToRgb8(ColorSpace.labToHex(lab));
            // Ảnh nhiễu phụ thuộc thiết bị có màu xám trung tính: đây là giá trị "đúng",
            // mọi số khác đều là tham số sai.
            assertThat(back.r())
                    .as("xám %d: làm tròn về 8-bit phải khớp", value)
                    .isEqualTo(value);
        }
    }

    @Test
    @DisplayName("Gamma: round-trip sRGB ↔ linear giữ nguyên giá trị trong [0,1]")
    void srgbLinearRoundTrip() {
        for (double encoded = 0.0; encoded <= 1.0; encoded += 1.0 / 512.0) {
            assertThat(ColorSpace.linearToSrgb(ColorSpace.srgbToLinear(encoded)))
                    .isCloseTo(encoded, within(1e-12));
        }
    }

    @Test
    @DisplayName("Nhiễu 8-bit ⇄ double: giá trị nguyên phải giữ nguyên khi khử gamma")
    void srgb8ToLinearKeepsBytesExact() {
        for (int value = 0; value <= 255; value++) {
            assertThat(ColorSpace.linearToSrgb8(ColorSpace.srgb8ToLinear(value)))
                    .as("byte %d phai khu gamma roi ghi lai giong nguyen", value)
                    .isEqualTo(value);
        }
    }

    // ------------------------------------------------------------------ anchor tuyệt đối

    @Test
    @DisplayName("Whitepoint lấy từ tổng hàng ma trận: #FFFFFF ra đúng L*=100, a*=b*=0 tuyệt đối")
    void whiteAnchor() {
        Lab white = ColorSpace.hexToLab("#FFFFFF");

        assertThat(white.l()).isCloseTo(100.0, within(1e-9));
        assertThat(white.a()).isCloseTo(0.0, within(1e-9));
        assertThat(white.b()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("Anchor đen: #000000 → L* = a* = b* = 0 (không âm do số 0 bị kẹp)")
    void blackAnchor() {
        Lab black = ColorSpace.hexToLab("#000000");

        assertThat(black.l()).isCloseTo(0.0, within(1e-12));
        assertThat(black.a()).isCloseTo(0.0, within(1e-12));
        assertThat(black.b()).isCloseTo(0.0, within(1e-12));
    }

    @Test
    @DisplayName("Anchor giữa thang: #808080 → L* ≈ 53,585 (giá trị tra cứu được)")
    void midGrayAnchor() {
        Lab gray = ColorSpace.hexToLab("#808080");

        // 128/255 không phải 0.5. Sai số ở đây chính là lỗi "quên chia 255" — rất hay gặp.
        assertThat(gray.l()).isCloseTo(53.585, within(5e-3));
        assertThat(gray.a()).isCloseTo(0.0, within(1e-9));
        assertThat(gray.b()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("L* tuân luật lũy thừa 1/3 trên Y, KHÔNG phải tuyến tính theo phần trăm sRGB")
    void lightnessFollowsTheCubeRootLawNotSrgbPercent() {
        // Sai lầm kinh điển: coi "50% xám" là L*=50. Không đúng — L* là hàm lũy thừa 1/3 của Y
        // (ánh sáng tuyến tính), nên 50% <b>sRGB</b> cho L* ≈ 53.6, còn 50% <b>linear</b> mới là 50.
        assertThat(ColorSpace.srgb8ToLab(128, 128, 128).l())
                .as("50% sRGB (byte 128) phai cho L* > 50")
                .isGreaterThan(53.0);

        for (double y : new double[] {0.02, 0.1, 0.2, 0.5, 0.8, 1.0}) {
            double expected = 116.0 * Math.cbrt(y) - 16.0;
            assertThat(ColorSpace.xyzToLab(new Xyz(0.0, y, 0.0)).l())
                    .as("L* tai Y=%s", y)
                    .isCloseTo(expected, within(1e-9));
        }
    }

    @Test
    @DisplayName("Whitepoint: XYZ của #FFFFFF phải bằng đúng D65 dùng để tính Lab")
    void whiteXyzEqualsD65() {
        Xyz xyz = ColorSpace.linearRgbToXyz(new RgbLinear(1.0, 1.0, 1.0));

        assertThat(xyz.x()).isCloseTo(Xyz.D65_WHITE_POINT.x(), within(1e-12));
        assertThat(xyz.y()).isCloseTo(1.0, within(1e-12));
        assertThat(xyz.z()).isCloseTo(Xyz.D65_WHITE_POINT.z(), within(1e-12));
        // D65 của CIE công bố lệch ~2.3e-4 ở Z — chứng minh lựa chọn whitepoint tự nhất quán với
        // ma trận chỉ khác bản công bố ở mức nhiễu, và nhỏ hơn nhiều so với sai số 0.5 ΔE00 mà
        // người dùng có thể nhìn thấy.
        assertThat(xyz.z() - Xyz.D65_CIE_PUBLISHED.z()).isBetween(-5e-4, 5e-4);
    }

    // ------------------------------------------------------------------ màu bão hoà

    @Test
    @DisplayName("Màu chính: Lab của 3 kênh bão hoà phải nằm trong giới hạn biết")
    void srgbPrimariesAreInKnownRange() {
        // Giá trị tham chiếu công bố (Lindbloom) được tính bằng bộ hằng số ma trận khác vài chữ
        // số thập phân cuối cùng, nên chấp nhận chênh 0.02. Sai số 0.02 là vô nghĩa so với ngưỡng
        // phân biệt ΔE00 ~2-3, nhưng sai lệch 5 L* thì là sai ma trận thật.
        assertThat(ColorSpace.hexToLab("#FF0000").l()).isCloseTo(53.2408, within(2e-2));
        assertThat(ColorSpace.hexToLab("#00FF00").l()).isCloseTo(87.7347, within(2e-2));
        assertThat(ColorSpace.hexToLab("#0000FF").l()).isCloseTo(32.2970, within(2e-2));

        // a* dương = đỏ, b* âm = xanh: kiểm tra dấu để bắt lỗi đảo kênh BGR/RGB.
        // #FF0000 = Lab(53.24, 80.09, 67.20) — dễ nhớ nhầm a* với b*, nên kiểm tra cả hai.
        assertThat(ColorSpace.hexToLab("#FF0000").a()).isPositive();
        assertThat(ColorSpace.hexToLab("#FF0000").a()).isCloseTo(80.0925, within(2e-2));
        assertThat(ColorSpace.hexToLab("#FF0000").b()).isCloseTo(67.2032, within(2e-2));
        assertThat(ColorSpace.hexToLab("#0000FF").b()).isNegative();
        assertThat(ColorSpace.hexToLab("#FFFF00").b()).isPositive();
    }

    @Test
    @DisplayName("Xanh lá có b* dương nhẹ — dùng để phát hiện lỗi đảo kênh khi khử gamma")
    void greenIsSlightlyYellow() {
        assertThat(ColorSpace.hexToLab("#00FF00").b())
                .as("#00FF00 phai la xanh la, b* duong nhe")
                .isPositive();
    }

    // ------------------------------------------------------------------ ngoài gamut

    @Test
    @DisplayName("Lab ngoài gamut: giữ nguyên giá trị âm trên linear, chỉ kẹp khi xuất hex")
    void outOfGamutIsHandledAtTheHexBoundary() {
        // Lab (100, 0, 0) tương đương linear trắng; kéo b* xuống rất âm sẽ đẩy R vượt 1 và G âm —
        // tức nằm ngoài lăng kính sRGB. Hệ thống phải sống sót việc này (ví dụ khi hiển thị màu
        // mà phép đo trả về nằm ngoài gamut) thay vì ném lỗi giữa chừng pipeline.
        Lab outOfGamut = new Lab(95.0, 40.0, -90.0);

        RgbLinear linear = ColorSpace.labToLinearRgb(outOfGamut);
        boolean anyOutOfGamut = linear.r() < 0.0 || linear.r() > 1.0
                || linear.g() < 0.0 || linear.g() > 1.0
                || linear.b() < 0.0 || linear.b() > 1.0;
        assertThat(anyOutOfGamut)
                .as("Lab (95,40,-90) phai nam ngoai lang kinh sRGB: %s", linear)
                .isTrue();

        String hex = ColorSpace.labToHex(outOfGamut);
        assertThat(hex).matches("#[0-9A-F]{6}");
    }

    // ------------------------------------------------------------------ dữ liệu đầu vào

    @Test
    @DisplayName("Hex sai định dạng bị từ chối, không âm thầm trả về màu đen")
    void malformedHexIsRejected() {
        assertThatThrownBy(() -> ColorSpace.hexToRgb8("FFF")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ColorSpace.hexToRgb8("#GGGGGG")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ColorSpace.hexToRgb8("#12345")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ColorSpace.hexToRgb8(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Hex nhận cả có/không dấu # và không phân biệt hoa thường")
    void hexParsingIsLenientAboutCaseAndHash() {
        assertThat(ColorSpace.hexToRgb8("#aabbcc")).isEqualTo(new ColorSpace.Srgb8(0xAA, 0xBB, 0xCC));
        assertThat(ColorSpace.hexToRgb8("AABBCC")).isEqualTo(new ColorSpace.Srgb8(0xAA, 0xBB, 0xCC));
    }

    @Test
    @DisplayName("Ma trận linear→XYZ trả về bản sao, không lộ trạng thái nội bộ")
    void matrixIsDefensivelyCopied() {
        double[] first = ColorSpace.linearSrgbToXyzMatrix();
        first[0] = 999.0;

        assertThat(ColorSpace.linearSrgbToXyzMatrix()[0])
                .as("sua mang tra ve khong duoc lam hong ma tran dung")
                .isNotEqualTo(999.0);
    }
}
