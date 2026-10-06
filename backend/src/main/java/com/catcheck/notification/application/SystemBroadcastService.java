package com.catcheck.notification.application;

import com.catcheck.notification.api.NotificationErrorCode;
import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.NotificationRequest;
import com.catcheck.notification.api.SystemBroadcastGateway;
import com.catcheck.notification.application.spi.NotificationRecipientPort;
import com.catcheck.notification.domain.NotificationTemplate;
import com.catcheck.shared.error.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L72 {@code POST /api/v1/admin/system/broadcast} — cai dat {@link SystemBroadcastGateway}.
 *
 * <p><b>Khong {@code @Transactional} tren ca lan phat.</b> Mot transaction bao 10.000 lan
 * {@code INSERT} giu khoa suot thoi gian do va se vo het neu dong thu 9.999 loi — tuc la mot
 * thong bao su co co han luat dinh 72 gio bien thanh "khong gui duoc cho ai". Thay vao do moi
 * nguoi nhan la mot transaction rieng (do {@code NotificationService.enqueue} mo), va
 * {@code dedupeKey} la thu lam cho mot lan bam lai an toan: nhung nguoi da duoc ghi se bi
 * {@code uq_*_dedupe} chan, nhung nguoi con thieu duoc ghi tiep.</p>
 *
 * <p><b>{@code dedupeKey} theo quy uoc p12 §12.4</b> {@code <template>:<refType>:<refId>:<moc>}.
 * O day {@code refId} la userId va {@code moc} la <b>phut</b> cua lan phat — khong phai
 * millisecond: hai lan bam trong cung mot phut gan nhu chac chan la mot lan bam doi (nguoi dung
 * tuong lan dau khong an), va dedupe nen chan no. Hai lan phat that cach nhau hon mot phut thi la
 * hai thong bao khac nhau va deu phai den.</p>
 */
@Service
public class SystemBroadcastService implements SystemBroadcastGateway {

    private static final Logger log = LoggerFactory.getLogger(SystemBroadcastService.class);

    /**
     * Kich thuoc lo keyset. 500 la can bang giua so lan round-trip va bo nho mot lo; cung bac
     * voi {@code batchSize} mac dinh cua {@code JobProperties}.
     */
    private static final int RECIPIENT_PAGE_SIZE = 500;

    private final NotificationGateway notifications;
    private final NotificationRecipientPort recipients;
    private final Clock clock;

    public SystemBroadcastService(NotificationGateway notifications,
                                  NotificationRecipientPort recipients,
                                  Clock clock) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.clock = clock;
    }

    @Override
    public BroadcastResult broadcast(String templateCode, String title, String body,
                                     Map<String, Object> payload, boolean dryRun) {
        if (!ALLOWED_TEMPLATE_CODES.contains(templateCode)) {
            // Kiem o day chu khong chi o controller: cong nay la be mat cong khai cua module,
            // va "chi hai template duoc broadcast" la rang buoc cua NGHIEP VU (p8 L72), khong
            // phai cua mot endpoint. Mot ben goi khac trong tuong lai phai chiu cung luat.
            throw new BusinessRuleException(
                    NotificationErrorCode.NOTIFICATION_TEMPLATE_UNKNOWN, templateCode);
        }
        NotificationTemplate template = NotificationTemplate.fromCode(templateCode)
                .orElseThrow(() -> new BusinessRuleException(
                        NotificationErrorCode.NOTIFICATION_TEMPLATE_UNKNOWN, templateCode));

        Instant startedAt = clock.instant();
        String dedupePrefix = template.code() + ":BROADCAST:" + startedAt.getEpochSecond() / 60;

        int matched = 0;
        int queued = 0;
        UUID cursor = null;
        while (true) {
            List<UUID> page = recipients.findActiveRecipientIdsAfter(cursor, RECIPIENT_PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            matched += page.size();
            if (!dryRun) {
                for (UUID userId : page) {
                    notifications.enqueue(new NotificationRequest(
                            userId, template.code(), payload, title, body,
                            null, null, dedupePrefix + ':' + userId));
                    queued++;
                }
            }
            cursor = page.getLast();
            if (page.size() < RECIPIENT_PAGE_SIZE) {
                break;
            }
        }

        log.info("Broadcast {} dryRun={} matched={} queued={}",
                template.code(), dryRun, matched, queued);
        return new BroadcastResult(matched, queued, dedupePrefix);
    }
}
