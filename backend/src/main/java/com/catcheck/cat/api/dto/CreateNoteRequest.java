package com.catcheck.cat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * D17 — body thêm ghi chú. Không có {@code scanId}: cột {@code cat_note.scan_id} chưa tồn tại ở
 * V8 (thêm ở V11, module {@code scan} — đang làm song song), nên client gửi field này (nếu có)
 * sẽ bị bỏ qua thay vì lỗi 400, để không chặn FE hiện có.
 */
public record CreateNoteRequest(
        String noteType,
        @NotBlank(message = "Nội dung ghi chú không được để trống")
        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
        String body,
        LocalDate occurredOn
) {
}
