package com.catcheck.scan.domain.color.port;

import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;

import java.util.List;
import java.util.Optional;

/**
 * Cổng đọc bảng màu pH + dải phân loại — tuyên bố ở phía scan, hiện thực ở module
 * {@code colorchart} (adapter {@code ColorChartCatalogAdapter}).
 *
 * <h2>Vì sao cổng nằm ở scan chứ không phải colorchart</h2>
 * <p>Chiều phụ thuộc là <b>colorchart → scan</b> (colorchart dùng {@code DeltaE2000}, {@code Lab}
 * của scan để validate publish). Nếu scan phụ thuộc colorchart thì thành vòng. Đảo ngược bằng
 * cách scan tuyên bố cổng cần và colorchart hiện thực — đúng chuẩn dependency inversion, và
 * ArchUnit không thấy cạnh nào đi tới colorchart từ scan.
 *
 * <p>Kiểu trả về là mô hình nội bộ của scan ({@link PhChart}, {@link PhBandClassifier.Band}) để
 * A6 không phải map lại từ DTO — adapter của colorchart làm phần đó.
 */
public interface ChartCatalog {

    /**
     * Bảng màu {@code ACTIVE} cho một (dòng sản phẩm, lô sản xuất).
     *
     * <p>{@code productionBatch} null nghĩa là tìm bản mặc định của dòng (không gắn lô) —
     * đúng quy tắc p6 §6.6.4: user không kích hoạt mã thì dùng bản {@code ACTIVE} mặc định
     * của {@code STANDARD}.
     *
     * @return empty nếu không có bảng {@code ACTIVE} nào — scan phải trả
     *         {@code SCAN_CHART_UNAVAILABLE} (p8 §8.2.4(e))
     */
    Optional<PhChart> findActiveChart(String productLine, String productionBatch);

    /**
     * Dải phân loại toàn cục ({@code chart_id IS NULL}) đang {@code active}, sắp theo
     * {@code sort_order}. Nguồn duy nhất cho S10 và cho {@code GET /reference/ph-bands} (F1).
     */
    List<PhBandClassifier.Band> findGlobalBands();
}
