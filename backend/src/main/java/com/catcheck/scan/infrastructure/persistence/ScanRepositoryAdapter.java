package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.Scan;
import com.catcheck.scan.domain.port.ScanRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Bộ chuyển đổi từ cổng {@link ScanRepository} sang Spring Data (R2). */
@Repository
class ScanRepositoryAdapter implements ScanRepository {

    private final ScanJpaRepository jpaRepository;

    ScanRepositoryAdapter(ScanJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Scan save(Scan scan) {
        return jpaRepository.save(scan);
    }

    @Override
    public Optional<Scan> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Scan> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return jpaRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }

    /**
     * {@code EntityManager.flush()} — đẩy MỌI entity đang chờ của persistence context hiện tại,
     * không riêng {@code scan}: {@code scan_analysis} và {@code scan_image} dùng chung một
     * {@code EntityManager} nên một lần gọi là đủ cho cả ba bảng.
     *
     * <p>{@code JdbcTemplate} dùng lại đúng connection mà {@code JpaTransactionManager} đã bind
     * vào {@code TransactionSynchronizationManager} (nó tự lấy {@code DataSource} từ
     * {@code EntityManagerFactoryInfo}), nên sau lệnh này câu SQL thô đọc được dữ liệu vừa ghi
     * dù transaction chưa commit.</p>
     */
    @Override
    public void flushPendingWrites() {
        jpaRepository.flush();
    }
}
