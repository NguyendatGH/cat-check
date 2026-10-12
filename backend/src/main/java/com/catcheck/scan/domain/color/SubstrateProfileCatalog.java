package com.catcheck.scan.domain.color;

import java.util.Optional;

/**
 * Port phía scan đọc màu nền chuẩn của dòng/lô cát. Nhánh không có thẻ dùng màu này làm đích
 * cân bằng trắng, thay vì giả định mọi loại cát đều phải xám tuyệt đối.
 */
public interface SubstrateProfileCatalog {

    Optional<Lab> findReferenceLab(String productLine, String productionBatch);
}
