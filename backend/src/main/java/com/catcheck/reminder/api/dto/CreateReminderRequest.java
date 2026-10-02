package com.catcheck.reminder.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

/** I2 — {@code POST /reminders}. */
public record CreateReminderRequest(
        String catId,
        @NotBlank String type,
        String scheduleKind,
        @Min(1) @Max(90) Integer intervalDays,
        @Size(max = 1024) String rrule,
        @JsonFormat(pattern = "HH:mm") LocalTime preferredTimeStart,
        @JsonFormat(pattern = "HH:mm") LocalTime preferredTimeEnd,
        @Size(max = 64) String timezone,
        List<String> channels,
        String source) {
}
