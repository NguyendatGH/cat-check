package com.catcheck.scan.domain;

import java.math.BigDecimal;

/**
 * Tổng hợp tác động của một lượt backfill preview — đúng hai con số mà p8 L34 đòi
 * ("số bản ghi bị <b>lật phân loại</b>, {@code deltaPh} lớn nhất") cộng phần mẫu số.
 *
 * <p>Tính bằng một câu SQL tổng hợp thay vì nạp hết dòng về rồi đếm trong Java: cửa sổ backfill
 * mặc định là P90D và trên hệ thống thật đó là hàng chục nghìn dòng — nạp hết chỉ để đếm là cách
 * nhanh nhất biến một màn admin thành một lần OOM.</p>
 *
 * @param evaluated    số bản ghi đã tính thử
 * @param flipped      số bản ghi bị lật phân loại
 * @param maxAbsDeltaPh {@code |deltaPh|} lớn nhất; {@code null} khi chưa có dòng nào
 * @param jobId        {@code job_run.id} của lượt preview; {@code null} khi chưa chạy
 */
public record ScanRecomputeImpact(
        long evaluated,
        long flipped,
        BigDecimal maxAbsDeltaPh,
        java.util.UUID jobId
) {

    public static ScanRecomputeImpact empty() {
        return new ScanRecomputeImpact(0L, 0L, null, null);
    }
}
