package com.catcheck.shared.job;

import java.time.ZoneId;

/**
 * Một dòng của danh mục job ở p12 §12.6, ở dạng máy đọc được.
 *
 * <p>Tồn tại vì {@code JobHeartbeatCheckJob} (p12 §12.6.6) phải so <b>lịch kỳ vọng</b> với
 * {@code job_run} gần nhất — nó cần biết cron của job khác, mà annotation {@code @Scheduled}
 * không đọc ngược được lúc chạy. Nguồn sự thật là {@code application.yml}: cùng một khoá cấu
 * hình vừa nuôi placeholder trong {@code @Scheduled} vừa nuôi record này, nên hai nơi không
 * thể lệch nhau.</p>
 *
 * @param name    đúng tên job ở p12 §12.6, không viết tắt (khớp {@code job_run.job_name})
 * @param cron    biểu thức cron 6 trường của Spring
 * @param zone    múi giờ của cron — mặc định Asia/Ho_Chi_Minh (p12 §12.6.1 quy tắc 1)
 * @param enabled job có được đăng ký ở môi trường này không
 */
public record JobDescriptor(String name, String cron, ZoneId zone, boolean enabled) {

    public JobDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("jobDescriptor.name phải có giá trị");
        }
        if (name.length() > 64) {
            // job_run.job_name là VARCHAR(64) (p4 §K3) — chặn ở đây thay vì để DB ném lúc 02:00.
            throw new IllegalArgumentException("jobDescriptor.name dài hơn 64 ký tự: " + name);
        }
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("jobDescriptor.cron phải có giá trị: " + name);
        }
        if (zone == null) {
            throw new IllegalArgumentException("jobDescriptor.zone phải có giá trị: " + name);
        }
    }
}
