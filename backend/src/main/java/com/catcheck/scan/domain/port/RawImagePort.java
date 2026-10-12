package com.catcheck.scan.domain.port;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.color.CardLayout;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.LinearizationCurve;
import com.catcheck.scan.domain.color.Matrix3;
import com.catcheck.scan.domain.color.port.VisionEngine;

import java.util.List;

public interface RawImagePort {

    DecodedImage decode(byte[] bytes);

    boolean isAvailable();

    QualityMetrics measureQuality(VisionEngine.FrameHandle frame);

    VisionEngine.FrameHandle cropToRoi(VisionEngine.FrameHandle frame, RoiRect roi);

    CalibrationResult calibrate(VisionEngine.FrameHandle roiFrame, Lab referenceSubstrateLab);

    List<CardPatchSample> sampleNeutralPatches(VisionEngine.FrameHandle canonicalCard, CardLayout cardLayout);

    CalibrationResult applyCardCcm(VisionEngine.FrameHandle roiFrame, LinearizationCurve curve, Matrix3 ccm,
                                    double residualDe00);

    record DecodedImage(VisionEngine.FrameHandle frame, int width, int height, String contentType) {
    }

    record RoiRect(double x, double y, double w, double h) {

        public RoiRect {
            if (w <= 0 || h <= 0) {
                throw new IllegalArgumentException("roi.w/h phai > 0");
            }
        }

        public static RoiRect fullFrame() {
            return new RoiRect(0.0, 0.0, 1.0, 1.0);
        }
    }

    record QualityMetrics(
            double blurVariance,
            double meanLuma,
            double clipHigh,
            double clipLow,
            double lumaGradient) {
    }

    record CalibrationResult(
            VisionEngine.XyzFrame xyzFrame,
            CalibrationMethod method,
            double[] wbGains,
            double neutralPixelRatio,
            double calibrationResidualDe00Proxy,
            Lab substrateLab) {
    }

    record CardPatchSample(String patchId, int encodedR, int encodedG, int encodedB, Lab referenceLab) {
    }

    class UnsupportedFormatException extends RuntimeException {
        public UnsupportedFormatException(String message) {
            super(message);
        }
    }

    class ImageDecodeException extends RuntimeException {
        public ImageDecodeException(String message) {
            super(message);
        }

        public ImageDecodeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
