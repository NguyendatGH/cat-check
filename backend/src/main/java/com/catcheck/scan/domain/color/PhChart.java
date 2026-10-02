package com.catcheck.scan.domain.color;

import java.util.List;

/**
 * Mô hình bảng màu phía scan — bản sao <b>chỉ đọc</b> của {@code color_chart} / {@code color_chart_point}
 * (p4 §4.9, p6 §6.6).
 *
 * <p>Cố tình <b>không</b> dùng chung kiểu với entity của module {@code colorchart}: kiểu ở đây thuộc
 * về phía scan, sống trong RAM, không mang ID bản ghi nào; entity kia sống trong DB và bị ràng buộc
 * bởi vòng đời publish. Trộn hai kiểu là cách nhanh nhất để khiến tầng đọc vô tình ghi vào dữ liệu
 * đang được hiệu chuẩn.
 *
 * @param id              mã bảng màu (UUID)
 * @param version         số thứ tự version, tăng mỗi lần publish
 * @param status          {@code DRAFT} | {@code ACTIVE} | {@code ARCHIVED}
 * @param illuminant      luôn {@code D65} — bất biến theo p6 §6.5 S5
 * @param observer        luôn {@code 2}
 * @param isPlaceholder   bảng seed tạm; UI hiện banner và bật trần confidence
 * @param points          các mức pH, <b>đã sắp theo phH tăng dần</b>
 * @param deltaEParams    tham số ΔE00 của bảng (mặc định của sản phẩm là kL=2)
 */
public record PhChart(
        String id,
        int version,
        Status status,
        String illuminant,
        int observer,
        boolean isPlaceholder,
        List<PhChartPoint> points,
        DeltaE2000Params deltaEParams) {

    public PhChart {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("Bang mau phai co it nhat mot muc pH");
        }
        points = List.copyOf(points);
        for (int i = 1; i < points.size(); i++) {
            if (points.get(i).phValue() < points.get(i - 1).phValue()) {
                throw new IllegalArgumentException(
                        "Cac muc pH phai don dieu tang dan; muc thu " + (i + 1) + " sai thu tu");
            }
        }
        deltaEParams = deltaEParams == null ? DeltaE2000Params.CAT_CHECK : deltaEParams;
    }

    /** Bảng có đang là bản đang dùng hay không. */
    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /** Dải pH hiển thị: không hard-code 5.5–8.5, đọc từ dữ liệu (p6 §6.7.3). */
    public double minPh() {
        return points.getFirst().phValue();
    }

    public double maxPh() {
        return points.getLast().phValue();
    }

    /** Chuẩn bị sẵn một bảng mức pH, dùng cho seed và cho kiểm thử. */
    public static PhChart of(String id, List<PhChartPoint> points) {
        return new PhChart(id, 1, Status.ACTIVE, "D65", 2, true, points, DeltaE2000Params.CAT_CHECK);
    }

    public enum Status {
        DRAFT, ACTIVE, ARCHIVED
    }
}
