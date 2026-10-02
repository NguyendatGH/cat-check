package com.catcheck.reminder.api;

import com.catcheck.reminder.api.dto.CreateReminderRequest;
import com.catcheck.reminder.api.dto.PatchReminderRequest;
import com.catcheck.reminder.api.dto.ReminderListResponse;
import com.catcheck.reminder.api.dto.ReminderResponse;
import com.catcheck.reminder.application.ReminderCommands;
import com.catcheck.reminder.application.ReminderService;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Nhóm I — Lịch nhắc theo dõi (p8 §8.4.9, 6 endpoint I1–I6). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reminder", description = "Lịch nhắc quét cát định kỳ và nhắc cấp tài khoản")
public class ReminderController {

    private final ReminderService service;

    public ReminderController(ReminderService service) {
        this.service = service;
    }

    /** I1 — {@code GET /reminders}. */
    @Operation(operationId = "listReminders", summary = "Danh sách lịch nhắc",
            description = "Lọc tuỳ chọn theo `catId` và `active`.")
    @GetMapping("/reminders")
    public ReminderListResponse list(
            @CurrentUser SecurityPrincipal user,
            @RequestParam(required = false) UUID catId,
            @RequestParam(required = false) Boolean active) {
        List<ReminderResponse> items = service.list(user.userId(), catId, active).stream()
                .map(ReminderResponse::from)
                .toList();
        return new ReminderListResponse(items);
    }

    /** I2 — {@code POST /reminders}. */
    @Operation(operationId = "createReminder", summary = "Tạo lịch nhắc",
            description = "`INTERVAL` (mỗi N ngày) hoặc `RRULE` (RFC 5545).")
    @PostMapping("/reminders")
    public ResponseEntity<ReminderResponse> create(
            @CurrentUser SecurityPrincipal user, @Valid @RequestBody CreateReminderRequest request) {
        var created = service.create(new ReminderCommands.Create(
                user.userId(),
                request.catId() == null || request.catId().isBlank() ? null : UUID.fromString(request.catId()),
                request.type(), request.scheduleKind(), request.intervalDays(), request.rrule(),
                request.preferredTimeStart(), request.preferredTimeEnd(), request.timezone(),
                request.channels(), request.source()));
        return ResponseEntity.created(URI.create("/api/v1/reminders/" + created.id()))
                .body(ReminderResponse.from(created));
    }

    /** I3 — {@code GET /reminders/{reminderId}}. */
    @Operation(operationId = "getReminder", summary = "Chi tiết một lịch nhắc")
    @GetMapping("/reminders/{reminderId}")
    public ReminderResponse detail(@CurrentUser SecurityPrincipal user, @PathVariable UUID reminderId) {
        return ReminderResponse.from(service.detail(user.userId(), reminderId));
    }

    /** I4 — {@code PATCH /reminders/{reminderId}}. */
    @Operation(operationId = "patchReminder", summary = "Sửa / bật / tắt lịch nhắc",
            description = "Merge-patch RFC 7396: field vắng mặt nghĩa là không đổi.")
    @PatchMapping(path = "/reminders/{reminderId}",
            consumes = { MediaType.APPLICATION_JSON_VALUE, "application/merge-patch+json" })
    public ReminderResponse patch(
            @CurrentUser SecurityPrincipal user,
            @PathVariable UUID reminderId,
            @Valid @RequestBody PatchReminderRequest request) {
        return ReminderResponse.from(service.patch(new ReminderCommands.Patch(
                user.userId(), reminderId, request.intervalDays(), request.scheduleKind(),
                request.rrule(), request.preferredTimeStart(), request.preferredTimeEnd(),
                request.channels(), request.active())));
    }

    /** I5 — {@code DELETE /reminders/{reminderId}}. Xoá mềm. */
    @Operation(operationId = "deleteReminder", summary = "Xoá lịch nhắc")
    @DeleteMapping("/reminders/{reminderId}")
    public ResponseEntity<Void> delete(@CurrentUser SecurityPrincipal user, @PathVariable UUID reminderId) {
        service.delete(user.userId(), reminderId);
        return ResponseEntity.noContent().build();
    }

    /**
     * I6 — {@code GET /reminders/{reminderId}/calendar.ics}.
     *
     * <p>File .ics TĨNH (p12 §12.5.6, C24): chỉ chứa một VEVENT ở mốc {@code nextRunAt}, KHÔNG
     * sinh chuỗi lặp. Lý do: {@code nextRunAt} được tính lại sau mỗi lần user quét (nhắc từ mốc
     * quét, không nhắc cứng theo lịch — p4 F1), nên một RRULE xuất ra đây sẽ lệch khỏi lịch thật
     * ngay lần quét kế tiếp. Người dùng tải lại file khi cần mốc mới.</p>
     */
    @Operation(operationId = "getReminderCalendar", summary = "Tải file .ics thêm vào lịch")
    @GetMapping(path = "/reminders/{reminderId}/calendar.ics", produces = "text/calendar")
    public ResponseEntity<String> calendar(
            @CurrentUser SecurityPrincipal user, @PathVariable UUID reminderId) {
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"catcheck-reminder-" + reminderId + ".ics\"")
                .body(service.calendarIcs(user.userId(), reminderId));
    }
}
