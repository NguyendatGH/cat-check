package com.catcheck.ai.application;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Dựng khối "THÔNG TIN BÉ MÈO" từ {@link CatContextPort.CatContext} để làm giàu prompt LLM / câu
 * trả lời fallback (nhiệm vụ wiring A2-A4, mục 3), và tính từ khoá nối thêm vào câu truy vấn tri
 * thức khi mèo có cờ cảnh báo đang mở (mục 4).
 *
 * <p><b>Ràng buộc cứng của nhiệm vụ này:</b> lớp này CHỈ diễn đạt lại SỐ LIỆU THÔ của
 * {@link CatContextPort.CatContext} — không thêm câu đánh giá kiểu "điều này có nghĩa là..." hay
 * khuyến nghị tự chế. Giữ mã phân loại/mã luật (vd {@code SLIGHTLY_HIGH},
 * {@code REPEATED_OUT_OF_RANGE}) nguyên văn thay vì tự dịch sang câu diễn giải y khoa.</p>
 *
 * <p>Không phải Spring bean — cùng kiểu {@code final class} tiện ích như {@link AiPromptRedactor},
 * dùng trực tiếp từ {@link AiChatService} trong cùng package.</p>
 */
final class CatContextPresenter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);

    /**
     * Từ khoá tiếng Việt nối thêm vào câu hỏi gốc trước khi tìm tri thức, theo {@code ruleCode}
     * của {@code health_flag} đang mở — PHẦN RETRIEVAL THUẦN TUÝ, không phải nội dung hiển thị
     * (xem {@link #augmentationKeywords}). {@code URGENT_CLINICAL_SIGN} cố ý KHÔNG có mặt: cờ đó
     * luôn rẽ sang nhánh khẩn cấp ở {@link AiChatService#chat} trước khi tới đây.
     */
    private static final Map<String, String> FLAG_KEYWORDS = Map.of(
            "REPEATED_OUT_OF_RANGE", "lệch pH nhiều lần",
            "BASELINE_DEVIATION", "khác mức thường ngày",
            "MONOTONIC_TREND", "xu hướng pH thay đổi",
            "LOW_QUALITY_STREAK", "chất lượng ảnh quét");

    private CatContextPresenter() {
    }

    /** {@code true} nếu có bất kỳ {@code health_flag} nào đang mở ở mức {@code URGENT}. */
    static boolean hasUrgentFlag(CatContextPort.CatContext context) {
        return context.openHealthFlags().stream().anyMatch(flag -> "URGENT".equals(flag.severity()));
    }

    /**
     * Từ khoá nối thêm vào câu hỏi TRƯỚC khi gọi {@code AiKnowledgePort.search} (mục 4) — chuỗi
     * rỗng nếu không có cờ nào khớp. Hiệu quả thực tế phụ thuộc kho {@code care_tip} đã có bài gắn
     * tag theo dải pH/cờ cảnh báo hay chưa; tại thời điểm viết, KHÔNG có bài nào (xem báo cáo bàn
     * giao) nên bước này hiện chỉ chuẩn bị sẵn cơ chế, chưa đổi kết quả tìm kiếm thực tế.
     */
    static String augmentationKeywords(CatContextPort.CatContext context) {
        return context.openHealthFlags().stream()
                .map(flag -> FLAG_KEYWORDS.get(flag.ruleCode()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(" "));
    }

    /**
     * Khối "THÔNG TIN BÉ MÈO" — chỉ liệt kê field CÓ GIÁ TRỊ, mỗi dòng một sự kiện. Trả về chuỗi
     * rỗng nếu không có field nào ngoài tên (trường hợp gần như không xảy ra vì {@code name} luôn
     * có ở {@code cat}, nhưng giữ phòng thủ cho test/fake).
     */
    static String renderContext(CatContextPort.CatContext context) {
        List<String> lines = new ArrayList<>();
        if (notBlank(context.name())) {
            lines.add("- Tên: " + context.name());
        }
        if (context.ageMonths() != null) {
            lines.add("- Tuổi: " + context.ageMonths() + " tháng");
        }
        if (notBlank(context.breedCode()) || notBlank(context.breedOther())) {
            lines.add("- Giống: " + (notBlank(context.breedOther()) ? context.breedOther() : context.breedCode()));
        }
        if (notBlank(context.sex())) {
            lines.add("- Giới tính: " + context.sex());
        }
        if (context.neutered() != null) {
            lines.add("- Triệt sản: " + (context.neutered() ? "đã triệt sản" : "chưa triệt sản"));
        }
        if (context.weightKg() != null) {
            String updated = context.weightUpdatedAt() != null
                    ? " (cập nhật " + DATE.format(context.weightUpdatedAt()) + ")" : "";
            lines.add("- Cân nặng: " + context.weightKg() + " kg" + updated);
        }
        CatContextPort.LatestScanSummary scan = context.latestScan();
        if (scan != null) {
            StringBuilder line = new StringBuilder("- Lần quét gần nhất (")
                    .append(scan.capturedAt() != null ? DATE.format(scan.capturedAt()) : "?")
                    .append("): pH ").append(scan.phValue())
                    .append(", phân loại ").append(scan.classification());
            if (scan.confidence() != null) {
                line.append(", độ tin cậy ").append(scan.confidence());
            }
            if (notBlank(scan.calibrationMethod())) {
                line.append(", hiệu chuẩn ").append(scan.calibrationMethod());
            }
            if (scan.nearBoundary()) {
                line.append(", gần ranh giới dải tham chiếu");
            }
            lines.add(line.toString());
        }
        CatContextPort.TrendSummary trend = context.last30Days();
        if (trend != null) {
            lines.add("- Tóm tắt 30 ngày gần đây: " + trend.scanCount() + " lần quét, trung vị pH "
                    + trend.medianPh() + ", tỉ lệ trong dải " + trend.inRangeRatio());
        }
        if (!context.openHealthFlags().isEmpty()) {
            String flags = context.openHealthFlags().stream()
                    .map(flag -> flag.ruleCode() + " (mức " + flag.severity() + ", từ "
                            + (flag.triggeredAt() != null ? DATE.format(flag.triggeredAt()) : "?") + ")")
                    .collect(Collectors.joining("; "));
            lines.add("- Cờ theo dõi đang mở: " + flags);
        }
        if (!context.recentClinicalSigns().isEmpty()) {
            String signs = context.recentClinicalSigns().stream()
                    .map(sign -> String.join(",", sign.signs())
                            + " (" + (sign.reportedAt() != null ? DATE.format(sign.reportedAt()) : "?")
                            + (sign.acknowledged() ? ", đã xem" : ", chưa xem") + ")")
                    .collect(Collectors.joining("; "));
            lines.add("- Dấu hiệu lâm sàng tự khai (14 ngày gần đây): " + signs);
        }
        CatContextPort.SurveySummary survey = context.survey();
        if (survey != null) {
            StringBuilder line = new StringBuilder("- Khảo sát sức khoẻ: loại cát=")
                    .append(dashIfBlank(survey.litterType()))
                    .append(", tiền sử tiết niệu=").append(dashIfBlank(survey.urinaryHistory()))
                    .append(", chế độ ăn=").append(dashIfBlank(survey.dietType()))
                    .append(", tần suất đi vệ sinh=").append(dashIfBlank(survey.urinationFrequency()));
            if (!survey.symptoms().isEmpty()) {
                line.append(", triệu chứng=").append(String.join(",", survey.symptoms()));
            }
            lines.add(line.toString());
        }
        if (!context.activeReminders().isEmpty()) {
            String reminders = context.activeReminders().stream()
                    .map(reminder -> reminder.type() + " (kế tiếp "
                            + (reminder.nextRunAt() != null ? DATE.format(reminder.nextRunAt()) : "?") + ")")
                    .collect(Collectors.joining("; "));
            lines.add("- Nhắc nhở đang bật: " + reminders);
        }
        CatContextPort.CreditSnapshot credit = context.credit();
        if (credit != null) {
            StringBuilder line = new StringBuilder("- Credit: còn lượt quét=").append(yesNo(credit.hasAvailableCredit()))
                    .append(", còn lượt dùng thử=").append(yesNo(credit.hasTrialScanRemaining()))
                    .append(", quyền ghi dữ liệu mới=").append(yesNo(credit.hasWriteAccess()));
            if (credit.maxCatProfiles() != null) {
                line.append(", tối đa hồ sơ mèo=").append(credit.maxCatProfiles());
            }
            lines.add(line.toString());
        }
        return lines.isEmpty() ? "" : "THÔNG TIN BÉ MÈO:\n" + String.join("\n", lines);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String dashIfBlank(String value) {
        return notBlank(value) ? value : "-";
    }

    private static String yesNo(boolean value) {
        return value ? "có" : "không";
    }
}
