package com.catcheck.scan.domain.color;

import java.util.Optional;

/**
 * Port phía scan đọc layout thẻ tham chiếu đang active theo mã — cùng mẫu thiết kế với
 * {@link ChartCatalog} (do {@code scan} tuyên bố, module {@code colorchart} hiện thực bằng
 * {@code ReferenceCardLayoutRepository}, xem {@code colorchart/package-info.java}).
 *
 * <p>Tách khỏi {@link ChartCatalog} vì đây là một mối quan tâm khác: layout thẻ (hình học +
 * màu in trên vật tư) chứ không phải bảng phân loại pH. Gộp hai cổng vào một interface sẽ buộc
 * mọi implementation của {@link ChartCatalog} (vd test double trong tương lai) phải biết cả về
 * thẻ dù không cần — vi phạm nguyên tắc tối thiểu.
 */
public interface CardLayoutCatalog {

    /**
     * Layout đang active (version mới nhất) của một mã thẻ, hoặc rỗng nếu chưa publish thẻ nào —
     * khi đó caller rơi về nhánh không CCM (xem {@code ColorPipelineService}).
     */
    Optional<CardLayout> findActiveLayout(String code);
}
