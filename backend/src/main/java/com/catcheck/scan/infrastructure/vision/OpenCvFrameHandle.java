package com.catcheck.scan.infrastructure.vision;

import com.catcheck.scan.domain.color.port.VisionEngine;
import org.bytedeco.opencv.opencv_core.Mat;

/**
 * Handle ảnh BGR 8-bit bất biến — bọc {@link Mat} của OpenCV.
 *
 * <p>Domain <b>không</b> đọc nội dung — nó chỉ chuyền handle qua lại giữa cổng và hạ tầng
 * (xem {@code VisionEngine.FrameHandle}). {@link Mat} được giải phóng trong {@link #close()}.
 */
public final class OpenCvFrameHandle implements VisionEngine.FrameHandle, AutoCloseable {

    private final Mat mat;
    private final VisionEngine.FrameSize size;

    public OpenCvFrameHandle(Mat mat) {
        if (mat == null || mat.empty()) {
            throw new IllegalArgumentException("Mat khong duoc rong");
        }
        this.mat = mat;
        this.size = new VisionEngine.FrameSize(mat.cols(), mat.rows());
    }

    public Mat mat() {
        return mat;
    }

    @Override
    public VisionEngine.FrameSize size() {
        return size;
    }

    @Override
    public void close() {
        mat.close();
    }
}
