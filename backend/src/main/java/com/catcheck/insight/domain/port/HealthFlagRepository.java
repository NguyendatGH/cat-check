package com.catcheck.insight.domain.port;

import com.catcheck.insight.domain.HealthFlag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HealthFlagRepository {

    /** {@code Optional.empty()} nếu {@code dedupe_key} đã tồn tại (cooldown chưa hết) — không ném lỗi. */
    Optional<HealthFlag> saveIfNotDuplicate(HealthFlag flag);

    Optional<HealthFlag> findById(UUID id);

    Page findByFilter(UUID userId, UUID catId, Boolean acknowledged, String severity, String cursor, int limit);

    HealthFlag save(HealthFlag flag);

    /** Thu hồi (xoá) các flag do đúng scan này kích hoạt — dùng khi đổi mèo (p6 §6.10.3). */
    List<UUID> deleteByTriggerScanId(UUID scanId);

    /**
     * Mèo sở hữu flag có thuộc {@code userId} không — dùng để kiểm {@code U:own} cho
     * {@code GET/POST /health-flags/{id}...} (p8 G2/G3). {@code health_flag} không có cột
     * {@code user_id} trực tiếp (p4 D12) nên phải tra qua {@code cat.owner_id}.
     */
    boolean isCatOwnedByUser(UUID catId, UUID userId);

    /** Flag đã bắn trong khoảng thời gian, mới nhất trước — nguồn cho {@code export} (p13 Khối 3). */
    List<HealthFlag> findByCatIdAndTriggeredAtBetween(UUID catId, java.time.Instant from, java.time.Instant to);

    record Page(List<HealthFlag> items, String nextCursor) {
    }
}
