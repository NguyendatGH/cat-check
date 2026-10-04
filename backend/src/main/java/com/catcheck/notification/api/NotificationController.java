package com.catcheck.notification.api;

import com.catcheck.notification.api.dto.CursorPage;
import com.catcheck.notification.api.dto.NotificationListResponse;
import com.catcheck.notification.api.dto.NotificationView;
import com.catcheck.notification.api.dto.UnreadCountResponse;
import com.catcheck.notification.application.NotificationService;
import com.catcheck.notification.domain.port.NotificationRepository;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Nhóm G — hộp thư thông báo in-app (p8 §8.4.7, G4–G8).
 *
 * <p>Auth của cả 5 endpoint là {@code U} / {@code U:own}; không có cổng entitlement
 * ({@code E:}) nào, nên {@code SecurityConfig.anyRequest().authenticated()} là đủ. Quy tắc sở
 * hữu tài nguyên của p8 §8.3.4 ("404 thay vì 403") được ép ở tầng service: mọi truy vấn đều
 * mang {@code user_id}.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Notification", description = "Hộp thư thông báo trong app (channel = IN_APP)")
public class NotificationController {

    private static final int DEFAULT_LIMIT = 20;

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** G4 — {@code GET /notifications}. */
    @Operation(operationId = "listNotifications", summary = "Hộp thư thông báo trong app",
            description = "Chỉ trả `channel = IN_APP` và bỏ các dòng đã ẩn/bị bỏ qua. "
                    + "Phân trang con trỏ `(created_at, id)` giảm dần; `limit` mặc định 20, tối đa 100 "
                    + "(vượt ⇒ 400 VALIDATION_FAILED, không âm thầm kẹp xuống).")
    @GetMapping("/notifications")
    public NotificationListResponse list(
            @CurrentUser SecurityPrincipal user,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        NotificationRepository.Page page = service.listInbox(user.userId(), cursor, effectiveLimit);
        List<NotificationView> items = page.items().stream()
                .map(NotificationDtoMapper::toView)
                .toList();
        return new NotificationListResponse(items,
                new CursorPage(effectiveLimit, page.nextCursor(), page.hasMore()));
    }

    /** G5 — {@code GET /notifications/unread-count}. */
    @Operation(operationId = "getUnreadNotificationCount", summary = "Số thông báo chưa đọc",
            description = "Đếm nhanh cho badge chuông — dùng partial index của p4 F2.")
    @GetMapping("/notifications/unread-count")
    public UnreadCountResponse unreadCount(@CurrentUser SecurityPrincipal user) {
        return new UnreadCountResponse(service.countUnread(user.userId()));
    }

    /** G6 — {@code POST /notifications/{notificationId}/read}. */
    @Operation(operationId = "markNotificationRead", summary = "Đánh dấu một thông báo đã đọc",
            description = "`204`. Idempotent: gọi lại trên thông báo đã đọc vẫn `204`.")
    @PostMapping("/notifications/{notificationId}/read")
    public ResponseEntity<Void> markRead(@CurrentUser SecurityPrincipal user,
                                         @PathVariable UUID notificationId) {
        service.markRead(user.userId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    /** G7 — {@code POST /notifications/read-all}. */
    @Operation(operationId = "markAllNotificationsRead", summary = "Đánh dấu toàn bộ đã đọc",
            description = "`204`.")
    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllRead(@CurrentUser SecurityPrincipal user) {
        service.markAllRead(user.userId());
        return ResponseEntity.noContent().build();
    }

    /** G8 — {@code DELETE /notifications/{notificationId}}. */
    @Operation(operationId = "hideNotification", summary = "Ẩn thông báo khỏi hộp thư",
            description = "Xoá mềm: bản ghi vẫn còn để giải thích \"vì sao tôi nhận được thông báo này\".")
    @DeleteMapping("/notifications/{notificationId}")
    public ResponseEntity<Void> hide(@CurrentUser SecurityPrincipal user,
                                     @PathVariable UUID notificationId) {
        service.hide(user.userId(), notificationId);
        return ResponseEntity.noContent().build();
    }
}
