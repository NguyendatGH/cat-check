package com.catcheck.scan.domain.color.port;

import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.RgbLinear;
import com.catcheck.scan.domain.color.Xyz;

import java.util.List;

public interface VisionEngine {

    CardDetectionResult detectCard(FrameHandle image, QuadHint hint);

    List<Blob> selectIndicatorGrains(XyzFrame xyzImage, Lab substrateLab, GrainThresholds thresholds);

    record CardDetectionResult(QuadHint quad, FrameHandle canonicalCard, Method method) {

        public enum Method {
            ARUCO,
            QR,
            CONTOUR,
            NONE
        }

        public boolean found() {
            return method != Method.NONE;
        }

        public boolean isUsable() {
            return found() && canonicalCard != null;
        }
    }

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

    record Blob(int pixelCount, com.catcheck.scan.domain.color.Lab labMean, double areaPx) {
    }

    record GrainThresholds(double specularLMax, double shadowLMin, double chromaMin,
                           double deltaEFromSubstrateMin, double clusterMaxDeltaE, int minBlobPx) {

        public static GrainThresholds standard() {
            return new GrainThresholds(95.0, 15.0, 10.0, 8.0, 14.0, 12);
        }
    }

    record FrameSize(int width, int height) {
    }

    interface FrameHandle {

        FrameSize size();
    }

    interface XyzFrame extends FrameHandle {

        Xyz at(int x, int y);

        int pixelCount();
    }

    static RgbLinear corrected(RgbLinear rgb, double[] wbGains, double[] ccm) {
        RgbLinear balanced = rgb.times(wbGains[0], wbGains[1], wbGains[2]);
        return new RgbLinear(
                ccm[0] * balanced.r() + ccm[1] * balanced.g() + ccm[2] * balanced.b(),
                ccm[3] * balanced.r() + ccm[4] * balanced.g() + ccm[5] * balanced.b(),
                ccm[6] * balanced.r() + ccm[7] * balanced.g() + ccm[8] * balanced.b());
    }
}
