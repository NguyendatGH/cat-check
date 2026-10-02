package com.catcheck.cat.domain.port;

import com.catcheck.cat.domain.CatHealthSurvey;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc/ghi bảng {@code cat_health_survey} (D18, D19).
 */
public interface CatHealthSurveyRepository {

    /** D19 — bản ghi gần nhất của mèo, bất kể đã gửi hay bỏ qua. */
    Optional<CatHealthSurvey> findLatestByCatId(UUID catId);

    /** Toàn bộ lịch sử, mới nhất trước — dùng cho hồ sơ PDF và cho việc nhắc lại. */
    List<CatHealthSurvey> findHistoryByCatId(UUID catId, int limit);

    /**
     * Có bản ghi ĐÃ GỬI chưa: trả về {@code false} nếu bản ghi mới nhất là bản bỏ qua, vì "bỏ qua"
     * khác "chưa hỏi" và lần sau vẫn nên hỏi lại.
     */
    boolean hasSubmittedByCatId(UUID catId);

    CatHealthSurvey save(CatHealthSurvey survey);
}
