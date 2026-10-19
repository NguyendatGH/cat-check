package com.catcheck.scan.domain;

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
