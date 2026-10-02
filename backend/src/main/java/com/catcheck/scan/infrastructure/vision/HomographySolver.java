package com.catcheck.scan.infrastructure.vision;

/**
 * Tính ma trận homography 3×3 từ 4 cặp điểm tương ứng — thuật toán DLT (Direct Linear
 * Transform).
 *
 * <p>JavaCPP 4.14 không bind {@code cv::getPerspectiveTransform} (chỉ có bản Cũ
 * {@code cvGetPerspectiveTransform} trả {@code CvMat} đã deprecated). Thay vì dùng API cũ, tính
 * homography bằng phương trình tuyến tính — đúng tinh thần "toán thuần Java" của dự án và
 * kiểm thử được mà không cần native.
 *
 * <p>Hệ phương trình: với mỗi cặp (x,y) → (x',y'), hai phương trình:
 * <pre>
 *   h11·x + h12·y + h13 − h31·x·x' − h32·y·x' − h33·x' = 0
 *   h21·x + h22·y + h23 − h31·x·y' − h32·y·y' − h33·y' = 0
 * </pre>
 * 4 cặp → 8 phương trình, 9 ẩn số. Đặt h33 = 1 (homography có 8 bậc tự do) → hệ 8×8.
 */
final class HomographySolver {

    private HomographySolver() {
    }

    /**
     * Tính homography từ 4 điểm nguồn tới 4 điểm đích.
     *
     * @param src 4 điểm nguồn, mỗi điểm {@code [x, y]}
     * @param dst 4 điểm đích, mỗi điểm {@code [x, y]}
     * @return ma trận 3×3, hàng-chính
     */
    static double[][] solve(double[][] src, double[][] dst) {
        if (src.length != 4 || dst.length != 4) {
            throw new IllegalArgumentException("Homography can dung 4 diem");
        }

        // Hệ 8×8: mỗi cặp điểm cho 2 phương trình
        double[][] a = new double[8][8];
        double[] b = new double[8];

        for (int i = 0; i < 4; i++) {
            double x = src[i][0];
            double y = src[i][1];
            double xp = dst[i][0];
            double yp = dst[i][1];

            // Phương trình cho x'
            a[i * 2][0] = x;
            a[i * 2][1] = y;
            a[i * 2][2] = 1;
            a[i * 2][3] = 0;
            a[i * 2][4] = 0;
            a[i * 2][5] = 0;
            a[i * 2][6] = -x * xp;
            a[i * 2][7] = -y * xp;
            b[i * 2] = xp;

            // Phương trình cho y'
            a[i * 2 + 1][0] = 0;
            a[i * 2 + 1][1] = 0;
            a[i * 2 + 1][2] = 0;
            a[i * 2 + 1][3] = x;
            a[i * 2 + 1][4] = y;
            a[i * 2 + 1][5] = 1;
            a[i * 2 + 1][6] = -x * yp;
            a[i * 2 + 1][7] = -y * yp;
            b[i * 2 + 1] = yp;
        }

        double[] h = solveLinear(a, b);

        return new double[][] {
                {h[0], h[1], h[2]},
                {h[3], h[4], h[5]},
                {h[6], h[7], 1.0}
        };
    }

    /**
     * Giải hệ tuyến tính Ax = b bằng Gauss có chọn trục cột.
     */
    private static double[] solveLinear(double[][] a, double[] b) {
        int n = b.length;
        double[][] augmented = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, augmented[i], 0, n);
            augmented[i][n] = b[i];
        }

        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(augmented[row][col]) > Math.abs(augmented[pivot][col])) {
                    pivot = row;
                }
            }
            if (Math.abs(augmented[pivot][col]) < 1e-12) {
                throw new IllegalStateException("He phuong trinh homography suy thoang");
            }
            double[] swap = augmented[col];
            augmented[col] = augmented[pivot];
            augmented[pivot] = swap;

            for (int row = col + 1; row < n; row++) {
                double factor = augmented[row][col] / augmented[col][col];
                for (int k = col; k <= n; k++) {
                    augmented[row][k] -= factor * augmented[col][k];
                }
            }
        }

        double[] x = new double[n];
        for (int row = n - 1; row >= 0; row--) {
            double sum = augmented[row][n];
            for (int col = row + 1; col < n; col++) {
                sum -= augmented[row][col] * x[col];
            }
            x[row] = sum / augmented[row][row];
        }
        return x;
    }
}
