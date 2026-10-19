package com.catcheck.scan.infrastructure.vision;

import com.catcheck.scan.domain.color.port.VisionEngine;
import org.bytedeco.opencv.opencv_core.Mat;

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
