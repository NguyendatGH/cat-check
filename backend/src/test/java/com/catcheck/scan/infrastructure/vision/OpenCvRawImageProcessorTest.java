package com.catcheck.scan.infrastructure.vision;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.port.RawImagePort;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OpenCvRawImageProcessorTest {

    private static OpenCvNativeLoader nativeLoader;

    @BeforeAll
    static void loadNative() {
        nativeLoader = new OpenCvNativeLoader();
        invokePostConstruct(nativeLoader);
        assumeTrue(nativeLoader.isAvailable(),
                "OpenCV native khong nap duoc trong moi truong nay - bo qua test can Mat that");
    }

    @Test
    @DisplayName("Nen cat xam-be cua dong STANDARD duoc dung cho SUBSTRATE_WB")
    void grayBeigeSubstrate_isAcceptedForWhiteBalance() throws IOException {
        // Lab xap xi (77.7, 2.0, 12.0), nam gan profile STANDARD seed
        // (66.814, 3.154, 10.461) nhung chroma > ngưỡng cũ 6.0.
        RawImagePort.CalibrationResult result = calibrateFlatRgb(205, 190, 170);

        assertThat(result.method()).isEqualTo(CalibrationMethod.SUBSTRATE_WB);
        assertThat(result.neutralPixelRatio()).isGreaterThanOrEqualTo(0.15);
    }

    @Test
    @DisplayName("Vung xanh bao hoa khong bi nhan nham la nen cat de can bang trang")
    void saturatedGreen_isNotAcceptedAsSubstrate() throws IOException {
        RawImagePort.CalibrationResult result = calibrateFlatRgb(40, 200, 140);

        assertThat(result.method()).isEqualTo(CalibrationMethod.NONE);
        assertThat(result.neutralPixelRatio()).isZero();
    }

    private RawImagePort.CalibrationResult calibrateFlatRgb(int r, int g, int b) throws IOException {
        BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB);
        int rgb = (r << 16) | (g << 8) | b;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, rgb);
            }
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "png", bytes)).isTrue();

        RawImagePort processor = new OpenCvRawImageProcessor(nativeLoader);
        RawImagePort.DecodedImage decoded = processor.decode(bytes.toByteArray());
        return processor.calibrate(decoded.frame(), new Lab(66.814, 3.154, 10.461));
    }

    private static void invokePostConstruct(OpenCvNativeLoader loader) {
        try {
            Method load = OpenCvNativeLoader.class.getDeclaredMethod("load");
            load.setAccessible(true);
            load.invoke(loader);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Khong goi duoc OpenCvNativeLoader.load()", e);
        }
    }
}
