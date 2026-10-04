package com.catcheck.notification.api;

import com.catcheck.notification.api.dto.CursorPage;
import com.catcheck.notification.api.dto.PushSubscriptionListResponse;
import com.catcheck.notification.api.dto.PushSubscriptionView;
import com.catcheck.notification.api.dto.UpsertPushSubscriptionRequest;
import com.catcheck.notification.application.PushSubscriptionCommands;
import com.catcheck.notification.application.PushSubscriptionService;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Nhóm G — thiết bị nhận web push (p8 §8.4.7, G9–G11).
 *
 * <p><b>Không có {@code POST /devices/register}.</b> p3 F10 và bản cũ của p4 dùng tên đó với
 * bảng {@code device_token}; tên chuẩn là {@code push_subscription} +
 * {@code PUT /push/subscriptions} (p8 §8.4.7 ghi chú, H9.2).</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Push subscription", description = "Thiết bị đang nhận thông báo đẩy (FCM web push)")
public class PushSubscriptionController {

    private final PushSubscriptionService service;

    public PushSubscriptionController(PushSubscriptionService service) {
        this.service = service;
    }

    /** G9 — {@code GET /push/subscriptions}. */
    @Operation(operationId = "listPushSubscriptions", summary = "Thiết bị đang nhận push của tôi",
            description = "Không phân trang (trần cứng 10 thiết bị) nhưng vẫn dùng envelope chung của p8 §8.1.4.")
    @GetMapping("/push/subscriptions")
    public PushSubscriptionListResponse list(@CurrentUser SecurityPrincipal user) {
        List<PushSubscriptionView> items = service.list(user.userId()).stream()
                .map(NotificationDtoMapper::toView)
                .toList();
        return new PushSubscriptionListResponse(items, CursorPage.unpaged(items.size()));
    }

    /** G10 — {@code PUT /push/subscriptions}. */
    @Operation(operationId = "upsertPushSubscription", summary = "Đăng ký / cập nhật thiết bị nhận push",
            description = "Upsert theo `fid` (hoặc `legacyToken`). Cần consent `HEALTH_REMINDER_PUSH` "
                    + "(thiếu ⇒ 403 CONSENT_REQUIRED); thiếu cả hai định danh ⇒ 400 PUSH_SUBSCRIPTION_INVALID; "
                    + "vượt 10 thiết bị ⇒ 409 PUSH_SUBSCRIPTION_LIMIT.")
    @PutMapping("/push/subscriptions")
    public PushSubscriptionView upsert(@CurrentUser SecurityPrincipal user,
                                       @Valid @RequestBody UpsertPushSubscriptionRequest request,
                                       HttpServletRequest httpRequest) {
        return NotificationDtoMapper.toView(service.upsert(new PushSubscriptionCommands.Upsert(
                user.userId(),
                request.fid(),
                request.legacyToken(),
                request.platform(),
                request.deviceLabel(),
                httpRequest.getHeader("User-Agent"))));
    }

    /** G11 — {@code DELETE /push/subscriptions/{subscriptionId}}. */
    @Operation(operationId = "revokePushSubscription", summary = "Gỡ đăng ký một thiết bị",
            description = "`revoke_reason = USER_DISABLED`. Bản ghi được giữ lại 30 ngày để "
                    + "`CleanupDeadPushTokensJob` dọn (p12 §12.3.9).")
    @DeleteMapping("/push/subscriptions/{subscriptionId}")
    public ResponseEntity<Void> revoke(@CurrentUser SecurityPrincipal user,
                                       @PathVariable UUID subscriptionId) {
        service.revoke(user.userId(), subscriptionId);
        return ResponseEntity.noContent().build();
    }
}
