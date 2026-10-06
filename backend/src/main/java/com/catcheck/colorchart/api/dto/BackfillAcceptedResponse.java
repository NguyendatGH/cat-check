package com.catcheck.colorchart.api.dto;

/**
 * Body của {@code 202} ở L33/L35. {@code Location} (header) trỏ tới L34 — nơi duy nhất biết
 * được {@code job_run.id} thật, vì dòng {@code job_run} chỉ mở sau khi job khởi động ở luồng nền.
 *
 * @param chartId   bảng màu đang backfill
 * @param window    cửa sổ đã dùng (chuẩn hoá về ISO-8601); {@code null} ở L35
 * @param statusUrl URL để xem tiến trình/kết quả — bằng chính giá trị header {@code Location}
 */
public record BackfillAcceptedResponse(
        String chartId,
        String window,
        String statusUrl
) {
}
