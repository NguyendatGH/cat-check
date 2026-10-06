package com.catcheck.scan.domain.color;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port used by scan to read the active pH chart and global classification bands.
 * The colorchart module supplies the implementation.
 */
public interface ChartCatalog {

    Optional<PhChart> findActiveChart(String productLine, String productionBatch);

    /**
     * Một bảng màu CỤ THỂ theo id, <b>bất kể trạng thái</b> — nền của backfill L33–L35
     * (p6 §6.5.4, §6.6.5).
     *
     * <p>Khác {@link #findActiveChart}: backfill phải tính thử theo bảng {@code DRAFT} (admin
     * muốn xem tác động <em>trước</em> khi publish) và theo bảng vừa {@code ACTIVE}. Lọc theo
     * trạng thái ở đây sẽ làm bước "xem trước" bất khả thi, nên việc quyết định trạng thái nào
     * được backfill nằm ở tầng application của colorchart, không nằm ở cổng đọc này.</p>
     */
    Optional<PhChart> findChartById(UUID chartId);

    List<PhBandClassifier.Band> findGlobalBands();
}
