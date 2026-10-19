package com.catcheck.shop.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.RedactedThrowable;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shop.api.ShopErrorCode;
import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.OrderStatusPolicy;
import com.catcheck.shop.domain.port.PaymentGateway;
import com.catcheck.shop.domain.port.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class AdminOrderService {
    private static final Logger log = LoggerFactory.getLogger(AdminOrderService.class);
    private static final int PAGE_SIZE = 20;

    private final ShopRepository repository;
    private final PaymentGateway gateway;
    private final AuditLogService auditLog;
    private final Clock clock;

    public AdminOrderService(ShopRepository repository, PaymentGateway gateway, AuditLogService auditLog, Clock clock) {
        this.repository = repository;
        this.gateway = gateway;
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

    /** The cancel reason lives only in the audit event (the audit writer redacts PII); {@code shop_order.cancel_reason} just says ADMIN. */
    public Order move(UUID id, String target, String reason, UUID adminId, String role) {
        Order order = get(id);
        String from = order.status();
        if (!OrderStatusPolicy.adminNextStatuses(order.paymentMethod(), from).contains(target)) throw invalid(from, target);
        boolean cancel = "CANCELLED".equals(target);
        String cancelReason = cancel ? AdminGuard.requireReason(reason) : null;
        if (cancel && "PENDING_PAYMENT".equals(from) && order.payosOrderCode() != null) closeLink(order);
        boolean moved = cancel ? repository.cancelAndRestock(id, from, "ADMIN", clock.instant(), false) : repository.transition(id, from, target);
        if (!moved) throw invalid(from, target); // someone else changed the order between our read and the compare-and-set
        AuditEvent.Builder event = AuditEvent.builder()
                .actor(AuditActor.admin(adminId, role))
                .subject(AuditSubjectType.SYSTEM, null)
                .action(cancel ? "SHOP_ORDER_CANCELLED" : "SHOP_ORDER_STATUS_CHANGED")
                .outcome(AuditOutcome.SUCCESS)
                .meta("orderId", id.toString())
                .meta("from", from)
                .meta("to", target);
        if (cancel) event.meta("reason", cancelReason);
        auditLog.record(event.build());
        return get(id);
    }

    // Best effort, like the buyer cancel: the link expires on its own, and a payment that lands meanwhile flags a refund.
    private void closeLink(Order order) {
        try {
            gateway.cancelLink(order.payosOrderCode(), "ADMIN_CANCELLED");
        } catch (RuntimeException e) {
            log.warn("PayOS cancelLink failed for order {} (code {})", order.id(), order.payosOrderCode(), RedactedThrowable.of(e));
        }
    }

    private static BusinessRuleException invalid(String from, String to) {
        return new BusinessRuleException(ShopErrorCode.ORDER_TRANSITION_INVALID, from, to);
    }
}
