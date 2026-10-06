package com.catcheck.shared.testing;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * {@link PlatformTransactionManager} không làm gì — để test đơn chạy được một
 * {@code TransactionTemplate} mà không cần DataSource.
 *
 * <p>Vì sao cần: {@code ChartBackfillService} mở transaction TƯỜNG MINH bằng
 * {@code TransactionTemplate} (thân job là self-invocation nên {@code @Transactional} sẽ bị bỏ
 * qua lặng lẽ — xem javadoc {@code launch}). Không có bản no-op này thì test đơn phải gọi thẳng
 * thân job và nhánh "đẩy sang luồng nền" không bao giờ được chạy.</p>
 */
public class NoOpTransactionManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
        // không có transaction thật để commit
    }

    @Override
    public void rollback(TransactionStatus status) {
        // không có transaction thật để rollback
    }
}
