package com.catcheck.cat.domain.port;

import com.catcheck.cat.domain.CatBreed;

import java.util.List;
import java.util.Optional;

/**
 * Cổng đọc danh mục {@code cat_breed} (F2, p4 C2).
 *
 * <p>Chỉ có đọc trong Phase 1: giống mèo là dữ liệu tham chiếu do V7 seed, không có màn hình quản
 * trị giống nên không có cổng ghi. Cần thêm giống mới thì thêm hàng trong migration, để mọi môi
 * trường đều có cùng danh mục.</p>
 */
public interface CatBreedRepository {

    /** Danh sách giống còn hiệu lực, đúng thứ tự {@code sort_order} để chip gợi ý không bị đảo. */
    List<CatBreed> findActive();

    Optional<CatBreed> findByCode(String code);
}
