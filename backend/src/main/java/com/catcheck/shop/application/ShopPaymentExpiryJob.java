package com.catcheck.shop.application;

import com.catcheck.shared.error.RedactedThrowable;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.application.JobRunner;
import com.catcheck.shop.domain.ExpiredPayment;
import com.catcheck.shop.domain.MarkPaidResult;
import com.catcheck.shop.domain.port.PaymentGateway;
import com.catcheck.shop.domain.port.ShopRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cancels PAYOS orders still unpaid after their payment window, after asking PayOS once more how much it received.
 * Fully paid ⇒ PAID; partly paid ⇒ cancelled with refund_needed. COD is never touched.
 */
@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.shop-payment-expiry", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ShopPaymentExpiryJob {

    public static final String JOB_NAME = "ShopPaymentExpiryJob";

    private static final Logger log = LoggerFactory.getLogger(ShopPaymentExpiryJob.class);

    private final JobRunner jobRunner;
    private final ShopRepository repository;
    private final PaymentGateway gateway;
    private final int batchSize;
    private final boolean dryRun;

    public ShopPaymentExpiryJob(
            JobRunner jobRunner,
            ShopRepository repository,
            PaymentGateway gateway,
            @Value("${catcheck.jobs.shop-payment-expiry.batch-size:100}") int batchSize,
            @Value("${catcheck.jobs.shop-payment-expiry.dry-run:false}") boolean dryRun) {
        this.jobRunner = jobRunner;
        this.repository = repository;
        this.gateway = gateway;
        this.batchSize = batchSize;
        this.dryRun = dryRun;
    }

    @Scheduled(cron = "${catcheck.jobs.shop-payment-expiry.cron:0 * * * * *}",
            zone = "${catcheck.jobs.zone:Asia/Ho_Chi_Minh}")
    @SchedulerLock(
            name = JOB_NAME,
            lockAtMostFor = "${catcheck.jobs.shop-payment-expiry.lock-at-most-for:PT50S}",
            lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.runScheduled(JOB_NAME, dryRun, this::expireUnpaidOrders);
    }

    // shortcut: one batch per run (every minute), upgrade to a loop if more than batch-size orders expire a minute.
    JobOutcome expireUnpaidOrders(JobContext context) {
        List<ExpiredPayment> expired = repository.expiredPayosOrders(context.startedAt(), batchSize);
        if (context.dryRun()) {
            return JobOutcome.of(expired.size(), 0, 0, "dry-run: " + expired.size() + " don PayOS qua han");
        }

        int handled = 0;
        int errors = 0;
        for (ExpiredPayment p : expired) {
            try {
                long paid = gateway.amountPaid(p.payosOrderCode());
                if (paid >= p.totalVnd()) {
                    MarkPaidResult result = repository.markPaid(p.payosOrderCode(), p.totalVnd(), "late-check", context.startedAt());
                    log.info("Don {} (code {}) da tra tien sat gio: {}", p.orderId(), p.payosOrderCode(), result);
                    if (result == MarkPaidResult.PAID) handled++;
                } else if (cancel(p, context, paid > 0)) {
                    handled++;
                }
            } catch (RuntimeException e) {
                // Includes amountPaid failing: an order that may be paid is left for the next run rather than cancelled.
                errors++;
                log.error("Khong xu ly duoc don {} (code {}) het han thanh toan", p.orderId(), p.payosOrderCode(), RedactedThrowable.of(e));
            }
        }
        return JobOutcome.of(handled, 0, errors,
                errors == 0 ? null : errors + " don het han loi, se thu lai o lan chay sau");
    }

    // No cancelLink: the PayOS link was created with expiredAt = payment_expires_at, so it has already expired.
    private boolean cancel(ExpiredPayment p, JobContext context, boolean moneyReceived) {
        boolean cancelled = repository.cancelAndRestock(p.orderId(), "PENDING_PAYMENT", "PAYMENT_EXPIRED", context.startedAt(), moneyReceived);
        if (cancelled && moneyReceived) {
            log.warn("Don {} (code {}) nhan thieu tien, da huy, tra kho va danh dau can hoan tien", p.orderId(), p.payosOrderCode());
        } else if (cancelled) {
            log.info("Don {} (code {}) het han thanh toan, da huy va tra kho", p.orderId(), p.payosOrderCode());
        }
        return cancelled;
    }
}
