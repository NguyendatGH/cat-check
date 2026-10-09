package com.catcheck.ai.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc ngữ cảnh MỘT bé mèo cụ thể cho trợ lý AI — snapshot CÓ CẤU TRÚC, KHÔNG PII chủ nuôi,
 * dựng tại thời điểm hỏi (xem {@code docs/cat-rag-credit-pipeline.md} mục A2-(1), A4-(1)).
 *
 * <p><b>Không đưa vào kho RAG chung.</b> Dữ liệu riêng của một bé mèo không bao giờ nạp vào
 * {@code ai_document}/{@code ai_chunk} — snapshot này chỉ tồn tại trong một lượt gọi
 * {@link #loadContext}, dùng để làm giàu prompt ngay lúc trả lời, không lưu lại để tìm kiếm theo
 * độ tương đồng (lý do: dữ liệu cá nhân trong kho chung rủi ro lộ chéo người dùng).</p>
 *
 * <p><b>Kiểm quyền sở hữu.</b> {@link #loadContext} trả {@link Optional#empty()} nếu
 * {@code catId} không thuộc {@code userId} — việc kiểm diễn ra ở
 * {@code cat.api.CatContextQuery} (điểm kiểm sở hữu DUY NHẤT của luồng này, xem javadoc ở đó).
 * Adapter hiện thực cổng này KHÔNG được gọi bất kỳ cổng nào khác (scan/insight/reminder/credit)
 * trước khi bước kiểm sở hữu đó trả về một snapshot.</p>
 *
 * <p><b>Cố ý KHÔNG có trong {@link CatContext}:</b> tên đầy đủ/email/số điện thoại/địa chỉ chủ
 * nuôi (snapshot không hề đọc bảng {@code app_user}), {@code public_code}, ảnh đại diện, và
 * {@code cat.notes} (trường tự do có thể chứa PII chủ nuôi gõ tay — xem javadoc
 * {@code cat.api.CatContextQuery}). Dữ liệu này sẽ bị đưa thẳng vào prompt LLM nên lược bỏ là
 * lựa chọn an toàn nhất, không phải sơ suất.</p>
 */
public interface CatContextPort {

    Optional<CatContext> loadContext(UUID userId, UUID catId);

    /**
     * @param latestScan         {@code null} nếu mèo chưa có lần quét hợp lệ nào
     * @param last30Days         {@code null} nếu có ít hơn 2 lần quét hợp lệ trong 30 ngày gần nhất
     * @param openHealthFlags    cờ {@code health_flag} CHƯA acknowledge
     * @param recentClinicalSigns bản khai dấu hiệu lâm sàng trong 14 ngày gần nhất
     * @param survey              {@code null} nếu chưa khảo sát hoặc lần gần nhất là "bỏ qua"
     */
    record CatContext(
            UUID catId,
            String name,
            Integer ageMonths,
            String breedCode,
            String breedOther,
            String sex,
            Boolean neutered,
            BigDecimal weightKg,
            Instant weightUpdatedAt,
            LatestScanSummary latestScan,
            TrendSummary last30Days,
            List<OpenHealthFlag> openHealthFlags,
            List<RecentClinicalSign> recentClinicalSigns,
            SurveySummary survey,
            List<ActiveReminder> activeReminders,
            CreditSnapshot credit) {

        public CatContext {
            openHealthFlags = openHealthFlags == null ? List.of() : List.copyOf(openHealthFlags);
            recentClinicalSigns = recentClinicalSigns == null ? List.of() : List.copyOf(recentClinicalSigns);
            activeReminders = activeReminders == null ? List.of() : List.copyOf(activeReminders);
        }
    }

    /** Nguồn: {@code scan.api.ScanHistoryQuery.RecentScan} (lần quét gần nhất, bất kể trong/ngoài cửa sổ 30 ngày). */
    record LatestScanSummary(
            Instant capturedAt,
            BigDecimal phValue,
            String classification,
            BigDecimal confidence,
            String calibrationMethod,
            boolean nearBoundary) {
    }

    /**
     * Thống kê 30 ngày gần nhất trên các lần quét hợp lệ (nguồn: cùng
     * {@code scan.api.ScanHistoryQuery.recentValidScans} dùng cho {@link #latestScan}).
     *
     * <p>Cố ý KHÔNG có "hướng xu hướng": việc diễn giải xu hướng (vd {@code MONOTONIC_TREND}) là
     * nghiệp vụ của bảng luật đã duyệt ở module {@code insight}, không phải phép tính tự chế ở
     * đây — xem {@link OpenHealthFlag} cho tín hiệu đó.</p>
     *
     * @param inRangeRatio tỉ lệ (0.0-1.0) số lần phân loại {@code IN_RANGE} trong cửa sổ
     */
    record TrendSummary(int scanCount, BigDecimal medianPh, BigDecimal inRangeRatio) {
    }

    /** {@code ruleCode}/{@code severity} thô (xem {@code insight.domain.MonitoringRuleCode}) — không tự diễn giải thành câu. */
    record OpenHealthFlag(String ruleCode, String severity, Instant triggeredAt) {
    }

    record RecentClinicalSign(List<String> signs, Instant reportedAt, boolean acknowledged) {
    }

    /** Mã thô của khảo sát 5 câu (p4 §4.4.4) — việc dịch sang câu tiếng Việt là của tầng wiring sau. */
    record SurveySummary(
            String litterType,
            String urinaryHistory,
            String dietType,
            String urinationFrequency,
            List<String> symptoms) {
    }

    /** {@code type} thô theo {@code reminder.domain.ReminderType}. */
    record ActiveReminder(String type, Instant nextRunAt) {
    }

    /**
     * Cờ quyền đọc được an toàn qua {@code credit.api} hiện có — KHÔNG có số dư ledger chi tiết
     * hay gói hiện tại dạng chuỗi: {@code EntitlementQuery.snapshot()} trả kiểu
     * {@code credit.domain.Entitlement} (ngoài named interface "api"), và sửa {@code credit/**}
     * để công bố thêm field nằm ngoài phạm vi nhiệm vụ này (xem báo cáo bàn giao).
     */
    record CreditSnapshot(
            boolean hasAvailableCredit,
            boolean hasTrialScanRemaining,
            boolean hasWriteAccess,
            Integer maxCatProfiles) {
    }
}
