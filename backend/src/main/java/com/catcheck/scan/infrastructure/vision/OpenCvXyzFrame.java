package com.catcheck.scan.infrastructure.vision;

import com.catcheck.scan.domain.color.Xyz;
import com.catcheck.scan.domain.color.port.VisionEngine;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.opencv_core.Mat;

public final class OpenCvXyzFrame implements VisionEngine.XyzFrame, AutoCloseable {

    private final Mat mat;
    private final int width;
    private final int height;

    public OpenCvXyzFrame(Mat mat) {
        if (mat == null || mat.empty()) {
            throw new IllegalArgumentException("Mat XYZ khong duoc rong");
        }
        if (mat.channels() != 3) {
            throw new IllegalArgumentException("Mat XYZ phai co 3 kenh, nhan duoc " + mat.channels());
        }
        this.mat = mat;
        this.width = mat.cols();
        this.height = mat.rows();
    }

    @Override
    public Xyz at(int x, int y) {
        DoublePointer ptr = new DoublePointer(mat.ptr(y, x));
        return new Xyz(ptr.get(0), ptr.get(1), ptr.get(2));
    }

    @Override
    public int pixelCount() {
        return width * height;
    }

    @Override
    public VisionEngine.FrameSize size() {
        return new VisionEngine.FrameSize(width, height);
    }

    public double[] toDoubleArray() {
        Mat contiguous = mat.isContinuous() ? mat : mat.clone();
        try {
            double[] out = new double[width * height * 3];
            new DoublePointer(contiguous.data()).get(out);
            return out;
        } finally {
            if (contiguous != mat) {
                contiguous.close();
            }
        }
    }

    public Mat mat() {
        return mat;
    }

    @Override
    public void close() {
        mat.close();
    }
}
