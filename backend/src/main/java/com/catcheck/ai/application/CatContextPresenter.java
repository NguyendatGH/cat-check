package com.catcheck.ai.application;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class CatContextPresenter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);

    private static final Map<String, String> FLAG_KEYWORDS = Map.of(
            "REPEATED_OUT_OF_RANGE", "lệch pH nhiều lần",
            "BASELINE_DEVIATION", "khác mức thường ngày",
            "MONOTONIC_TREND", "xu hướng pH thay đổi",
            "LOW_QUALITY_STREAK", "chất lượng ảnh quét");

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
