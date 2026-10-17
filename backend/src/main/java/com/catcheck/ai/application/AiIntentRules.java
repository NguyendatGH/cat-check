package com.catcheck.ai.application;

import java.util.List;

/** Phân loại intent bằng luật cục bộ (không dùng LLM phụ — docs/AI_HYBRID_RAG_PLAN.md mục 5). */
final class AiIntentRules {

    // So trên văn bản đã bỏ dấu, có khoảng trắng hai đầu để chỉ khớp nguyên cụm từ.
    private static final List<String> DIAGNOSIS_OR_DOSAGE = List.of(
            " lieu luong ", " lieu dung ", " lieu thuoc ", " bao nhieu mg ", " mg kg ", " ke don ", " ke thuoc ",
            " uong thuoc gi ", " dung thuoc gi ", " chan doan ", " bi benh gi ", " mac benh gi ",
            " dosage ", " dose ", " diagnose ", " diagnosis ", " prescribe ", " what disease ");

    private AiIntentRules() {
    }

    static boolean asksForDiagnosisOrDosage(String question) {
        String folded = " " + AiText.fold(question).replaceAll("[^a-z0-9]+", " ").strip() + " ";
        return DIAGNOSIS_OR_DOSAGE.stream().anyMatch(folded::contains);
    }
}
