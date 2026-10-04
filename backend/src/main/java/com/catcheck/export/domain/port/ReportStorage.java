package com.catcheck.export.domain.port;

import java.io.InputStream;
import java.util.Optional;

/**
 * Nơi lưu file PDF hồ sơ sức khoẻ — <b>p13 §13.6.4 bảng "Nơi lưu"</b>: interface
 * {@code ReportStorage} với hiện thực {@code LocalFileSystemReportStorage} ghi vào
 * "thư mục riêng {@code .../reports/}, <b>không chung thư mục với ảnh scan</b>".
 *
 * <p><b>Bug thật đã sửa (W2-A).</b> Trước đây {@code export} dùng
 * {@code media.api.ImageStorage.put("export-pdf", ...)} cho file PDF. Cổng đó là cổng ẢNH:
 * {@code catcheck.storage.local.allowed-content-types} chỉ nhận
 * {@code image/jpeg|png|webp}, nên mọi lần sinh PDF ném {@code STORAGE_UNSUPPORTED_TYPE} và
 * job kết thúc ở {@code FAILED}. Lỗi này bị che hoàn toàn bởi H15.76 — worker chạy trước
 * COMMIT nên chưa bao giờ đi tới bước lưu file; sửa H15.76 xong mới lộ ra.</p>
 *
 * <p>Vòng đời file ở đây khác hẳn ảnh scan (PDF 7 ngày vs ảnh 14 ngày — p4 §K5 nêu đúng lý do
 * này khi từ chối tạo bảng {@code stored_file} dùng chung), nên hai cổng tách nhau là đúng
 * kiến trúc, không phải tiện tay.</p>
 */
public interface ReportStorage {

    /** Ghi vào cột {@code export_job.storage_provider} (p4 G1): {@code LOCAL} | {@code CLOUDINARY}. */
    String provider();

    /**
     * Ghi file PDF, trả tham chiếu để lưu vào {@code export_job.file_ref}.
     *
     * @param documentCode mã tài liệu ({@code CC-EXP-2026-000123}) — dùng làm tên file
     * @param pdf          nội dung file
     */
    Stored put(String documentCode, byte[] pdf);

    /** Mở file để tải. Rỗng nếu tham chiếu không còn (job dọn đã xoá, hoặc file bị mất). */
    Optional<InputStream> open(String fileRef);

    /** Xoá file. Không ném lỗi nếu tham chiếu đã biến mất (job retention gọi lại được). */
    void delete(String fileRef);

    /**
     * @param provider {@link #provider()}
     * @param fileRef  tham chiếu TƯƠNG ĐỐI — không bao giờ là đường dẫn tuyệt đối hay URL
     *                 (p8 §8.1.3: không để lộ đường dẫn tệp)
     * @param bytes    kích thước thực tế đã ghi
     */
    record Stored(String provider, String fileRef, long bytes) {
    }
}
