package com.catcheck.reminder.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

public record PatchReminderRequest(
        String scheduleKind,
        @Min(1) @Max(90) Integer intervalDays,
        @Size(max = 1024) String rrule,
        @JsonFormat(pattern = "HH:mm") LocalTime preferredTimeStart,
        @JsonFormat(pattern = "HH:mm") LocalTime preferredTimeEnd,
        List<String> channels,
        Boolean active) {
}
