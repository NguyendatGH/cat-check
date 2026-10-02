package com.catcheck.privacy.api.dto;

import java.time.Instant;

/**
 * Một yêu cầu DSAR — response C13/C14 (p8 §8.4.3).
 *
 * @param publicRef    mã tra cứu
 * @param requestType  loại quyền (p15 §15.4.1)
 * @param channel      kênh nộp
 * @param status       trạng thái xử lý
 * @param receivedAt   mốc bắt đầu đồng hồ SLA
 * @param ackDueAt     hạn phản hồi 02 ngày làm việc
 * @param fulfilDueAt  hạn thực hiện 10/15/20 ngày theo loại
 */
public record DsarRequestView(
        String publicRef,
        String requestType,
        String channel,
        String status,
        Instant receivedAt,
        Instant ackDueAt,
        Instant fulfilDueAt
) {
}
