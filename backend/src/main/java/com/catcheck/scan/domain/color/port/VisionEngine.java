package com.catcheck.scan.domain.color.port;

import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.RgbLinear;
import com.catcheck.scan.domain.color.Xyz;

import java.util.List;

/**
 * Cổng cho phần thị giác — giao diện phía scan tuyên bố, phần hiện thực nằm ở
 * {@code com.catcheck.scan.infrastructure.vision} (p6 S3, S6).
 *
 * <h2>Vì sao cần cổng</h2>
 * <p>Kiểu dữ liệu hình ảnh không được xuất hiện trong tầng domain: domain phải chạy được và được
 * kiểm thử trên máy không có thư viện native. Kiểu dữ liệu hình ảnh cũng không phải mảng
 * {@code double[]} — mảng phẳng dễ làm lẫn thứ tự kênh, và đó là loại bug đã được chính p6 §6.5 S5
 * gọi tên. Vì vậy ảnh đi vào cổng dưới dạng <b>handle bất biến</b> do hạ tầng cấp, và chỉ số 8-bit
 * đi ra cũng dưới dạng handle.
 */
public interface VisionEngine {

    /**
     * S3 — phát hiện thẻ tham chiếu và nắn phẳng về khung chuẩn 600×380.
     *
     * <p>Thứ tự thử bắt buộc: ArUco {@code DICT_4X4_50} → {@code cv::QRCodeDetector} → contour +
     * {@code approxPolyDP}. Marker ArUco đứng trước vì nó cho <b>tư thế</b> (homography) chứ không
     * chỉ khung bao, nên ảnh nắn không bị xoay/lệch góc — mà góc lệch chính là nguồn sai số hệ thống
     * lớn nhất sau CCM.
     *
     * @param image ảnh vào (BGR 8-bit, đã áp EXIF orientation)
     * @param hint  góc bốn điểm gợi ý từ client, có thể rỗng
     * @return kết quả phát hiện; {@code method = NONE} nghĩa là rơi vào nhánh dự phòng S4b
     */
    CardDetectionResult detectCard(FrameHandle image, QuadHint hint);

    /**
     * S6 — tách các hạt chỉ thị khỏi nền cát, trên ảnh đã hiệu chuẩn.
     *
     * <p>Đầu vào là <b>ảnh đã nâng lên XYZ</b> chứ không phải BGR: ngưỡng tách dựa trên
     * {@code L*}, chroma và khoảng cách màu so với nền — ba đại lượng chỉ tồn tại trong không gian
     * này. Chuyển sang XYZ ở đây cũng giữ được lời cấm {@code cvtColor} 8-bit: số 8-bit đi vào là
     * <b>một</b> byte/kênh, còn toàn bộ phép quyết định diễn ra trên {@code double}.
     *
     * @param xyzImage        ảnh XYZ (float/double), 3 kênh
     * @param substrateLab    màu nền cát ước lượng
     * @param thresholds      ngưỡng tách của dòng sản phẩm
     * @return các hạt tìm được, mỗi hạt là một danh sách pixel XYZ
     */
    List<Blob> selectIndicatorGrains(XyzFrame xyzImage, Lab substrateLab, GrainThresholds thresholds);

    /** Thông tin về thẻ sau khi phát hiện. */
    record CardDetectionResult(QuadHint quad, FrameHandle canonicalCard, Method method) {

        public enum Method {
            /** ArUco DICT_4X4_50 — phương án ưu tiên, có tư thế. */
            ARUCO,
            /** cv::QRCodeDetector. */
            QR,
            /** contour + approxPolyDP. */
            CONTOUR,
            /** Không thấy thẻ — chạy nhánh dự phòng S4b. */
            NONE
        }

        public boolean found() {
            return method != Method.NONE;
        }

        /** Thẻ tìm thấy nhưng vô dụng (ví dụ dải xám không đơn điệu) → CARD_INVALID, vẫn rơi về S4b. */
        public boolean isUsable() {
            return found() && canonicalCard != null;
        }
    }

    /**
     * Góc bốn điểm của thẻ, theo thứ tự <b>trái trên → phải trên → phải dưới → trái dưới</b>.
     *
     * <p>Thứ tự này là quy ước bắt buộc: hàm nắn phẳng ánh xạ vị trí ảnh tới ô nào trên thẻ. Đảo thứ
     * tự sẽ cho ảnh đúng hình nhưng mọi ô màu bị gán sai chỗ — một loại lỗi mà người review mã
     * không bao giờ nhìn ra.
     *
     * @param topLeft     đỉnh trên-trái
     * @param topRight    đỉnh trên-phải
     * @param bottomRight đỉnh dưới-phải
     * @param bottomLeft  đỉnh dưới-trái
     */
    record QuadHint(double[] topLeft, double[] topRight, double[] bottomRight, double[] bottomLeft) {

        public static final int POINT_COUNT = 4;

        public QuadHint {
            requirePair(topLeft, "topLeft");
            requirePair(topRight, "topRight");
            requirePair(bottomRight, "bottomRight");
            requirePair(bottomLeft, "bottomLeft");
        }

        public static QuadHint of(double[] topLeft, double[] topRight,
                                  double[] bottomRight, double[] bottomLeft) {
            return new QuadHint(topLeft, topRight, bottomRight, bottomLeft);
        }

        /** Góc rỗng — dùng khi client không gửi gợi ý, hoặc khi thẻ không được tìm thấy. */
        public static QuadHint empty() {
            double[] zero = new double[2];
            return new QuadHint(zero, new double[2], new double[2], new double[2]);
        }

        public boolean isEmpty() {
            return topLeft[0] == 0 && topRight[0] == 0 && bottomRight[0] == 0 && bottomLeft[0] == 0;
        }

        private static void requirePair(double[] point, String name) {
            if (point == null || point.length != 2) {
                throw new IllegalArgumentException("Dinh " + name + " phai la cap [x,y]");
            }
        }
    }

    /**
     * Một hạt chỉ thị: các pixel thuộc cùng một vùng liên thông trong không gian Lab.
     *
     * @param pixelCount số pixel
     * @param labMean    màu trung bình của hạt (sẽ đưa vào S7)
     * @param areaPx     diện tích theo pixel
     */
    record Blob(int pixelCount, com.catcheck.scan.domain.color.Lab labMean, double areaPx) {
    }

    /**
     * Ngưỡng tách hạt (p6 §6.5.1 S6).
     *
     * @param specularLMax         trần L* để loại pixel bị bắt sáng
     * @param shadowLMin           sàn L* để loại pixel quá tối
     * @param chromaMin            chroma tối thiểu để tách khỏi cát
     * @param deltaEFromSubstrateMin khoảng cách ΔE00 tối thiểu so với nền
     * @param clusterMaxDeltaE     ΔE00 tối đa để gộp hai cụm thành một hạt
     * @param minBlobPx            số pixel tối thiểu của một hạt
     */
    record GrainThresholds(double specularLMax, double shadowLMin, double chromaMin,
                           double deltaEFromSubstrateMin, double clusterMaxDeltaE, int minBlobPx) {

        /** Bộ ngưỡng mặc định của dòng STANDARD (p6 S6). */
        public static GrainThresholds standard() {
            return new GrainThresholds(95.0, 15.0, 10.0, 8.0, 14.0, 12);
        }
    }

    /** Xác định kích thước ảnh — thuần số, không phụ thuộc thư viện ảnh. */
    record FrameSize(int width, int height) {
    }

    /**
     * Handle ảnh bất biến do hạ tầng cấp. Domain <b>không</b> được đọc nội dung — nó chỉ chuyền
     * handle qua lại giữa cổng và hạ tầng.
     */
    interface FrameHandle {

        FrameSize size();
    }

    /** Ảnh XYZ: 3 kênh {@code double} trên mỗi pixel, dùng cho S6. */
    interface XyzFrame extends FrameHandle {

        /** XYZ tại một pixel. */
        Xyz at(int x, int y);

        /** Số pixel. */
        int pixelCount();
    }

    /** Áp white balance + CCM lên một màu linear (tiện cho cổng khác và cho A6). */
    static RgbLinear corrected(RgbLinear rgb, double[] wbGains, double[] ccm) {
        RgbLinear balanced = rgb.times(wbGains[0], wbGains[1], wbGains[2]);
        return new RgbLinear(
                ccm[0] * balanced.r() + ccm[1] * balanced.g() + ccm[2] * balanced.b(),
                ccm[3] * balanced.r() + ccm[4] * balanced.g() + ccm[5] * balanced.b(),
                ccm[6] * balanced.r() + ccm[7] * balanced.g() + ccm[8] * balanced.b());
    }
}
