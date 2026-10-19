package com.catcheck.shop.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shop.api.ShopErrorCode;
import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.OrderStatusPolicy;
import com.catcheck.shop.domain.port.ShopRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class AdminOrderService {
    private static final int PAGE_SIZE = 20;
    private static final int MIN_REASON_LENGTH = 5;

    private final ShopRepository repository;
    private final AuditLogService auditLog;
    private final Clock clock;

    public AdminOrderService(ShopRepository repository, AuditLogService auditLog, Clock clock) {
        this.repository = repository;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    public List<Order> list(String status, int page) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        return repository.adminOrders(status == null || status.isBlank() ? null : status, PAGE_SIZE,
                (int) Math.min((long) page * PAGE_SIZE, Integer.MAX_VALUE));
    }

    public Order get(UUID id) {
        return repository.adminOrder(id).orElseThrow(() -> new NotFoundException(ShopErrorCode.ORDER_NOT_FOUND));
    }

    /** The free-text cancel reason is only validated, never logged or audited: staff may type buyer details into it. */
    public Order move(UUID id, String target, String reason, UUID adminId, String role) {
        Order order = get(id);
        String from = order.status();
        if (!OrderStatusPolicy.adminNextStatuses(order.paymentMethod(), from).contains(target)) throw invalid(from, target);
        boolean cancel = "CANCELLED".equals(target);
        if (cancel && (reason == null || reason.strip().length() < MIN_REASON_LENGTH)) throw new BusinessRuleException(ShopErrorCode.CANCEL_REASON_REQUIRED);
        boolean moved = cancel ? repository.cancelAndRestock(id, from, "ADMIN", clock.instant()) : repository.transition(id, from, target);
        if (!moved) throw invalid(from, target); // someone else changed the order between our read and the compare-and-set
        auditLog.record(AuditEvent.builder()
                .actor(AuditActor.admin(adminId, role))
                .subject(AuditSubjectType.SYSTEM, null)
                .action(cancel ? "SHOP_ORDER_CANCELLED" : "SHOP_ORDER_STATUS_CHANGED")
                .outcome(AuditOutcome.SUCCESS)
                .meta("orderId", id.toString())
                .meta("from", from)
                .meta("to", target)
                .build());
        return get(id);
    }

    private static BusinessRuleException invalid(String from, String to) {
        return new BusinessRuleException(ShopErrorCode.ORDER_TRANSITION_INVALID, from, to);
    }
}
