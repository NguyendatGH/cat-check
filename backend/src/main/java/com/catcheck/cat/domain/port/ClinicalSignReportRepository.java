package com.catcheck.cat.domain.port;

import com.catcheck.cat.domain.CatClinicalSignReport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc/ghi bảng {@code cat_clinical_sign_report} (p4 C5).
 *
 * <p>Không cổng xoá: bản khai dấu hiệu lâm sàng là dữ liệu bác sĩ đọc, nên Phase 1 chỉ thêm và đọc,
 * không sửa và không xoá — kể cả khi người dùng gõ nhầm. Sửa lịch sử y tế bằng cách xoá làm mất
 * khả năng truy vết, nên {@code DTO} của D17 cũng cố tình không có trường sửa.</p>
 */
public interface ClinicalSignReportRepository {

    Optional<CatClinicalSignReport> findById(UUID id);

    /** Lịch sử khai của một mèo, mới nhất trước — đưa vào hồ sơ PDF. */
    List<CatClinicalSignReport> findByCatId(UUID catId, int limit);

    long countByCatId(UUID catId);

    /**
     * Mèo nào đang có bản khai chưa được bác sĩ xác nhận — dùng cho màn hình "cần người xem xét"
     * và cho việc gỡ cờ khi bác sĩ mở hồ sơ.
     */
    List<CatClinicalSignReport> findUnacknowledged(int limit);

    CatClinicalSignReport save(CatClinicalSignReport report);
}
