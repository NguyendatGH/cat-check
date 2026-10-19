package com.catcheck.ai.application;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class CatContextPresenter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);

    private static final Map<String, String> FLAG_KEYWORDS = Map.of(
            "REPEATED_OUT_OF_RANGE", "lệch pH nhiều lần",
            "BASELINE_DEVIATION", "khác mức thường ngày",
            "MONOTONIC_TREND", "xu hướng pH thay đổi",
            "LOW_QUALITY_STREAK", "chất lượng ảnh quét");

    // Đáp án khảo sát là Map tự do: chỉ nhận mã kiểu CATCHECK_SMART / 1_2_PER_DAY (questionnaire/health-survey-v1.json).
    // Bắt buộc có chữ cái để số điện thoại toàn chữ số không lọt qua.
    private static final Pattern SURVEY_CODE = Pattern.compile("(?=.*[A-Z])[A-Z0-9_]{1,40}");

    private CatContextPresenter() {
    }

    static boolean hasUrgentFlag(CatContextPort.CatContext context) {
        return context.openHealthFlags().stream().anyMatch(flag -> "URGENT".equals(flag.severity()));
    }

    static String augmentationKeywords(CatContextPort.CatContext context) {
        return context.openHealthFlags().stream()
                .map(flag -> FLAG_KEYWORDS.get(flag.ruleCode()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(" "));
    }

    /**
     * Khối này đi sang provider bên ngoài: chỉ gồm mã/số có cấu trúc. Tên mèo và chữ người dùng tự gõ
     * (giống "khác") không bao giờ được đưa vào (docs/AI_HYBRID_RAG_PLAN.md mục 1, 6).
     */
    static String renderContext(CatContextPort.CatContext context) {
        List<String> lines = new ArrayList<>();
        if (context.ageMonths() != null) {
            lines.add("- Tuổi: " + context.ageMonths() + " tháng");
        }
        if (notBlank(context.breedCode()) || notBlank(context.breedOther())) {
            lines.add("- Giống: " + (notBlank(context.breedCode()) ? context.breedCode() : "khác"));
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
                    .append(surveyCode(survey.litterType()))
                    .append(", tiền sử tiết niệu=").append(surveyCode(survey.urinaryHistory()))
                    .append(", chế độ ăn=").append(surveyCode(survey.dietType()))
                    .append(", tần suất đi vệ sinh=").append(surveyCode(survey.urinationFrequency()));
            List<String> symptoms = survey.symptoms().stream().filter(CatContextPresenter::isSurveyCode).toList();
            if (!symptoms.isEmpty()) {
                line.append(", triệu chứng=").append(String.join(",", symptoms));
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

    private static boolean isSurveyCode(String value) {
        return value != null && SURVEY_CODE.matcher(value).matches();
    }

    private static String surveyCode(String value) {
        return isSurveyCode(value) ? value : "-";
    }

    private static String yesNo(boolean value) {
        return value ? "có" : "không";
    }
}
