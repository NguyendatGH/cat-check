/**
 * Xuất hồ sơ PDF (lịch sử scan + insight) để đưa cho bác sĩ thú y.
 *
 * <p>{@code scan::api} (lịch sử scan + ảnh thu nhỏ), {@code insight::api} (health_flag đã bắn
 * trong kỳ), {@code media::api} (giải mã/transcode ảnh WebP -> JPEG khi nhúng PDF, xem p13
 * §13.3 khối 5 — khối IMAGES chưa hiện thực nên hiện KHÔNG còn chỗ nào trong module import
 * {@code media.api}; giữ khai báo cho khối đó, xoá được nếu khối IMAGES bị loại khỏi phạm vi).
 * <b>File PDF sinh ra KHÔNG đi qua {@code media::api}</b> mà qua cổng riêng
 * {@code domain.port.ReportStorage} — p13 §13.6.4 đòi thư mục {@code reports/} riêng, và
 * {@code ImageStorage} chỉ nhận {@code image/*} (bug đã sửa ở W2-A, xem javadoc cổng đó). Không khai {@code identity}: module đó chưa expose named interface "api" với
 * query port đọc chủ nuôi ở M0 (chỉ event + DTO HTTP) — trang bìa PDF đọc trực tiếp bằng JDBC
 * read-only trên bảng {@code cat}/{@code app_user}, không import type Java của hai module đó
 * (cùng judgment call với {@code scan}, xem {@code docs/handovers/A6.md}). {@code cat::api} CÓ
 * được khai — chỉ để tái dùng {@code CatErrorCode.CAT_NOT_FOUND} (404, không phân biệt "không
 * tồn tại" với "không thuộc về bạn") thay vì định nghĩa lại, cùng lý do đã áp dụng cho
 * {@code CAT_ARCHIVED} ở module {@code scan}.
 * Không khai {@code notification}: thông báo "REPORT_PDF_READY" (p13 §13.6.3) là UX-sugar ngoài
 * phạm vi MVP — client poll {@code GET /exports/{jobId}} (p8 J3) là đủ để biết trạng thái.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = { "shared", "scan::api", "insight::api", "media::api", "cat::api" })
package com.catcheck.export;
