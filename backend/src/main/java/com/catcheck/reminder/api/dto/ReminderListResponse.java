package com.catcheck.reminder.api.dto;

import java.util.List;

public record ReminderListResponse(List<ReminderResponse> items) {
}
