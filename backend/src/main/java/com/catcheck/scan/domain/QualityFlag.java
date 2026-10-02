package com.catcheck.scan.domain;

/**
 * Một cờ chất lượng — phần tử của mảng {@code scan_analysis.quality_flags} (p4 D3).
 *
 * <p>{@code messageKey} là khoá i18n {@code scan.quality.<code>} (quyết định #15 — không lưu
 * text cứng); nhãn tiếng Việt/Anh tra ở {@code messages/scan_vi.properties}.
 */
public record QualityFlag(QualityFlagCode code, QualityFlagSeverity severity) {

    public QualityFlag {
        if (code == null || severity == null) {
            throw new IllegalArgumentException("qualityFlag.code va severity la bat buoc");
        }
    }

    public String messageKey() {
        return "scan.quality." + code.name();
    }
}
